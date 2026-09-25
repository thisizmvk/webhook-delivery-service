package com.webhook.delivery.dispatch

import com.webhook.delivery.domain.Delivery
import com.webhook.delivery.domain.Outcome
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class WebhookSender(private val restClient: RestClient) {

    /** Makes one HTTP attempt. Never throws; every result becomes an [Outcome]. */
    fun send(delivery: Delivery, attemptNumber: Int): Outcome =
        try {
            val response = restClient.post()
                .uri(delivery.destinationUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Delivery-Id", delivery.id.toString())   // lets receivers deduplicate
                .header("X-Event-Type", delivery.eventType)
                .header("X-Delivery-Attempt", attemptNumber.toString())
                .body(delivery.payload)
                .retrieve()
                .onStatus({ _ -> true }) { _, _ -> }   // don't throw on 4xx/5xx; RetryPolicy classifies them
                .toBodilessEntity()
            Outcome.Response(response.statusCode.value())
        } catch (ex: Exception) {
            Outcome.Error("${ex.javaClass.simpleName}: ${ex.message}")
        }
}