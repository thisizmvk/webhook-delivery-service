package com.webhook.delivery.api

import com.webhook.delivery.domain.Attempt
import com.webhook.delivery.domain.Delivery
import com.webhook.delivery.domain.DeliveryStatus
import java.time.Instant
import java.util.UUID

data class DeliveryResponse(
    val id: UUID,
    val status: DeliveryStatus,
    val destinationUrl: String,
    val eventType: String,
    val attemptCount: Int,
    val nextAttemptAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val attempts: List<AttemptResponse>,
) {
    companion object {
        fun from(delivery: Delivery) = DeliveryResponse(
            id = delivery.id,
            status = delivery.status,
            destinationUrl = delivery.destinationUrl,
            eventType = delivery.eventType,
            attemptCount = delivery.attempts.size,
            nextAttemptAt = delivery.nextAttemptAt,
            createdAt = delivery.createdAt,
            updatedAt = delivery.updatedAt,
            attempts = delivery.attempts.map(AttemptResponse::from),
        )
    }
}

data class AttemptResponse(
    val number: Int,
    val startedAt: Instant,
    val durationMs: Long,
    val httpStatus: Int?,
    val error: String?,
) {
    companion object {
        fun from(attempt: Attempt) = AttemptResponse(
            number = attempt.number,
            startedAt = attempt.startedAt,
            durationMs = attempt.durationMs,
            httpStatus = attempt.httpStatus,
            error = attempt.error,
        )
    }
}