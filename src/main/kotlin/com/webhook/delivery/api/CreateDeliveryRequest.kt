package com.webhook.delivery.api

import com.webhook.delivery.domain.Delivery
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant

data class CreateDeliveryRequest(
    @field:NotBlank
    @field:Pattern(regexp = "^https?://\\S+$", message = "must be an http or https URL")
    val destinationUrl: String?,

    @field:NotBlank
    @field:Size(max = 100)
    val eventType: String?,

    @field:NotNull
    val payload: Map<String, Any?>?,
) {
    // Only called after @Valid has passed, so the requireNotNull calls never fail.
    fun toDelivery(idempotencyKey: String?, now: Instant): Delivery =
        Delivery.create(
            destinationUrl = requireNotNull(destinationUrl),
            eventType = requireNotNull(eventType),
            payload = requireNotNull(payload),
            idempotencyKey = idempotencyKey,
            now = now,
        )
}