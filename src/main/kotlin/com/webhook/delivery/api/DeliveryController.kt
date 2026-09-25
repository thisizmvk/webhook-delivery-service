package com.webhook.delivery.api

import com.webhook.delivery.store.DeliveryRepository
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.time.Clock
import java.util.UUID

@RestController
@RequestMapping("/deliveries")
class DeliveryController(
    private val repository: DeliveryRepository,
    private val clock: Clock,
) {

    /**
     * Accepts a delivery for asynchronous processing.
     * 202 Accepted: a new delivery was created.
     * 200 OK: the Idempotency-Key matched an existing delivery, which is returned unchanged.
     */
    @PostMapping
    fun create(
        @Valid @RequestBody request: CreateDeliveryRequest,
        @RequestHeader("Idempotency-Key", required = false) idempotencyKey: String?,
    ): ResponseEntity<DeliveryResponse> {
        val candidate = request.toDelivery(idempotencyKey, clock.instant())
        val stored = repository.createIfAbsent(candidate)
        val body = DeliveryResponse.from(stored)

        return if (stored.id == candidate.id) {
            ResponseEntity.accepted().location(URI.create("/deliveries/${stored.id}")).body(body)
        } else {
            ResponseEntity.ok(body)
        }
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): DeliveryResponse =
        repository.findById(id)?.let(DeliveryResponse::from)
            ?: throw DeliveryNotFoundException(id)
}