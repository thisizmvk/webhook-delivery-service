package com.webhook.delivery.domain

import java.time.Instant
import java.util.UUID

data class Delivery(
    val id: UUID,
    val destinationUrl: String,
    val eventType: String,
    val payload: Map<String, Any?>,
    val idempotencyKey: String?,
    val status: DeliveryStatus,
    val attempts: List<Attempt>,
    val nextAttemptAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val nextAttemptNumber: Int
        get() = attempts.size + 1

    fun isDue(now: Instant): Boolean =
        (status == DeliveryStatus.PENDING || status == DeliveryStatus.RETRY_SCHEDULED) &&
                nextAttemptAt != null && !nextAttemptAt.isAfter(now)

    fun markInFlight(now: Instant): Delivery {
        check(status == DeliveryStatus.PENDING || status == DeliveryStatus.RETRY_SCHEDULED) {
            "Cannot start an attempt for delivery $id in status $status"
        }
        return copy(status = DeliveryStatus.IN_FLIGHT, updatedAt = now)
    }

    companion object {
        fun create(
            destinationUrl: String,
            eventType: String,
            payload: Map<String, Any?>,
            idempotencyKey: String?,
            now: Instant,
        ) = Delivery(
            id = UUID.randomUUID(),
            destinationUrl = destinationUrl,
            eventType = eventType,
            payload = payload,
            idempotencyKey = idempotencyKey,
            status = DeliveryStatus.PENDING,
            attempts = emptyList(),
            nextAttemptAt = now,
            createdAt = now,
            updatedAt = now,
        )
    }
    fun recordAttempt(attempt: Attempt, decision: Decision, now: Instant): Delivery {
        check(status == DeliveryStatus.IN_FLIGHT) {
            "Cannot record an attempt for delivery $id in status $status"
        }
        val allAttempts = attempts + attempt
        return when (decision) {
            Decision.Succeed -> copy(
                status = DeliveryStatus.SUCCEEDED, attempts = allAttempts,
                nextAttemptAt = null, updatedAt = now,
            )
            is Decision.Retry -> copy(
                status = DeliveryStatus.RETRY_SCHEDULED, attempts = allAttempts,
                nextAttemptAt = now.plus(decision.after), updatedAt = now,
            )
            Decision.GiveUp -> copy(
                status = DeliveryStatus.DEAD, attempts = allAttempts,
                nextAttemptAt = null, updatedAt = now,
            )
        }
    }
}