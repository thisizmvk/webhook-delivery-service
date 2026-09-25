package com.webhook.delivery.dispatch

import com.webhook.delivery.config.WebhookProperties
import com.webhook.delivery.domain.Attempt
import com.webhook.delivery.domain.Delivery
import com.webhook.delivery.domain.Outcome
import com.webhook.delivery.domain.RetryPolicy
import com.webhook.delivery.store.DeliveryRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class Dispatcher(
    private val repository: DeliveryRepository,
    private val sender: WebhookSender,
    private val retryPolicy: RetryPolicy,
    private val properties: WebhookProperties,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${webhook.poll-interval-ms:500}")
    fun dispatchDue() {
        repository.findDue(clock.instant(), properties.batchSize).forEach { delivery ->
            // One bad delivery must never stop the loop for the others.
            try {
                deliver(delivery)
            } catch (ex: Exception) {
                log.error("Unexpected error while dispatching delivery {}", delivery.id, ex)
            }
        }
    }

    fun deliver(delivery: Delivery) {
        val attemptNumber = delivery.nextAttemptNumber
        val inFlight = repository.save(delivery.markInFlight(clock.instant()))

        val startedAt = clock.instant()
        val startNanos = System.nanoTime()
        val outcome = sender.send(inFlight, attemptNumber)
        val durationMs = (System.nanoTime() - startNanos) / 1_000_000

        val attempt = Attempt(
            number = attemptNumber,
            startedAt = startedAt,
            durationMs = durationMs,
            httpStatus = (outcome as? Outcome.Response)?.status,
            error = (outcome as? Outcome.Error)?.message,
        )
        val decision = retryPolicy.decide(attemptNumber, outcome)
        val updated = repository.save(inFlight.recordAttempt(attempt, decision, clock.instant()))

        log.info(
            "Delivery {} attempt {} finished: outcome={} decision={} status={}",
            delivery.id, attemptNumber, outcome, decision, updated.status,
        )
    }
}
