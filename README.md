# Webhook Delivery Service

Clients submit an event and a destination URL. The service delivers the event in the background,
retries when the destination has a temporary problem, and keeps a record of every attempt so you
can see what happened.

Kotlin, Spring Boot 4.1, Gradle.

## Running locally

You need JDK 17 or newer.

```bash
./gradlew bootRun
./gradlew test
```

The app starts on port 8080. `requests.http` has example requests you can run from IntelliJ,
or use curl:

```bash
curl -i -X POST localhost:8080/deliveries \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: demo-1' \
  -d '{"destinationUrl":"https://httpbin.org/status/503","eventType":"order.created","payload":{"orderId":42}}'

curl localhost:8080/deliveries/<id>
```

That destination always returns 503, so you can watch the attempts pile up and the delivery
eventually give up. The example relies on httpbin.org being reachable; any URL that returns
5xx works the same way.

## API

`POST /deliveries` takes a body like this:

```json
{
  "destinationUrl": "https://example.com/hook",
  "eventType": "order.created",
  "payload": { "orderId": 42 }
}
```

It returns 202 with the new delivery. If the `Idempotency-Key` header matches a delivery that
already exists, it returns 200 with that delivery instead of creating a second one. Invalid input
gets a 400 with a list of the fields that failed.

`GET /deliveries/{id}` returns the delivery's status, when the next attempt is due, and every
attempt so far with its HTTP status or error and how long it took. Unknown ids get a 404.

Errors follow the ProblemDetail format (RFC 9457).

When the service calls a destination, it POSTs the payload as JSON and adds three headers:
`X-Delivery-Id`, `X-Event-Type` and `X-Delivery-Attempt`.

## How delivery works

A new delivery starts as `PENDING`. A scheduled job picks up anything that's due, marks it
`IN_FLIGHT` and calls the destination. Based on the result it ends up as one of:

- `SUCCEEDED` if the destination returned 2xx
- `RETRY_SCHEDULED` if the failure looks temporary and there are attempts left
- `DEAD` if the failure is permanent or it ran out of attempts

I treat 408, 429, 5xx, timeouts and connection errors as temporary. Everything else, mostly 4xx,
is permanent, because the receiver has rejected the request and sending it again won't help.

Retry delays grow exponentially and use full jitter: each delay is a random value between zero
and `base-backoff * 2^(attempt - 1)`, capped at `max-backoff`. The randomness keeps retries from
many deliveries from hitting a recovering destination at the same moment.

## Configuration

Everything is in `application.properties`:

| Property | Default | What it does |
|---|---|---|
| `webhook.connect-timeout` | `2s` | Connection timeout for each attempt |
| `webhook.read-timeout` | `5s` | How long to wait for a response |
| `webhook.max-attempts` | `5` | Attempts before a delivery is marked `DEAD` |
| `webhook.base-backoff` | `1s` | Starting point for retry delays |
| `webhook.max-backoff` | `5m` | Longest possible retry delay |
| `webhook.batch-size` | `50` | Deliveries handled per dispatcher run |
| `webhook.poll-interval-ms` | `500` | Pause between dispatcher runs |

## When something goes wrong

The first place to look is `GET /deliveries/{id}`, which shows every attempt and why it failed.
The dispatcher also logs one line per attempt with the delivery id, the result and what it
decided to do next. `GET /actuator/health` reports whether the app is up.

## Code layout

- `api` has the controller, request and response classes, and error handling
- `domain` has `Delivery`, `Attempt` and `RetryPolicy`, with no Spring dependencies
- `store` has the repository interface and the in-memory implementation
- `dispatch` has the scheduled dispatcher and the HTTP client code
- `config` has the configuration properties and bean setup

## Assumptions

Delivery is at-least-once. A destination can receive the same event more than once, for example
if it processed a request but timed out before responding. Receivers should use `X-Delivery-Id`
to ignore duplicates.

I also assumed that delivery order doesn't matter, callers are trusted (there's no auth), and
idempotency keys are global since there's no concept of separate clients yet.

The payload must be a JSON object. Arrays and bare values are rejected with a 400, which keeps
the contract simple and leaves room to add fields to the envelope later.

## Tradeoffs

Delivery happens in the background rather than during the POST request, so a slow or broken
destination never slows down the caller. The cost is that callers have to poll to find out what
happened.

Storage is in memory, which means pending deliveries are lost on restart. It kept the scope
manageable, and because everything goes through the `DeliveryRepository` interface, swapping in
a database shouldn't touch the rest of the code. Nothing is ever evicted, and the dispatcher
scans all deliveries on each run. That's fine at this scale, and a database with an index on
due time would replace both.

The dispatcher is a single scheduled loop. It's easy to follow, but it only works with one
instance, and a slow destination holds up the others until its read timeout kicks in.

`Delivery` is immutable, and each state change goes through a method that checks the transition
is allowed. It's a bit more code than mutating fields directly, but the lifecycle is much easier
to reason about and test.

For testing, I put my time into unit tests for the retry policy, since that's where most of the
decisions live. The API and dispatcher were checked by hand using `requests.http`.

## What I left out

Integration tests, metrics, persistence, recovery for deliveries stuck in `IN_FLIGHT` after a
crash, request signing, authentication, protection against internal destination URLs, circuit
breaking, support for `Retry-After`, and a way to redeliver dead deliveries.

## What I'd do next

1. Integration tests that run the whole flow against a fake destination (MockWebServer), covering
   success, retry then success, permanent failure, running out of attempts, timeouts,
   idempotency, validation and 404s. The MockWebServer and Awaitility dependencies are already
   in the build for this.
2. Metrics for succeeded, retried and dead deliveries plus attempt duration, and the delivery id
   in every log line so one search shows a delivery's whole history.
3. Postgres storage using `SELECT ... FOR UPDATE SKIP LOCKED`, so multiple instances can share
   the work safely.
4. A timeout on `IN_FLIGHT`, so deliveries interrupted by a crash get retried.
5. Blocking private and internal addresses as destinations. Right now anyone who can call the
   API can make this service send requests inside the network, which is the most serious gap.
6. HMAC signatures so receivers can check that a request really came from this service.
7. An endpoint to redeliver dead deliveries.
8. A circuit breaker and a worker pool per destination, so one failing receiver can't slow
   everything else down.
9. Rejecting an idempotency key that's reused with a different request body.