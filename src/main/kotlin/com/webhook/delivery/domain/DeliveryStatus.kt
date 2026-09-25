package com.webhook.delivery.domain

enum class DeliveryStatus {
    PENDING,  // accepted, first attempt not yet made
    IN_FLIGHT, //an attempt is currently being made
    RETRY_SCHEDULED, // last attempt failed with a retryable outcome
    SUCCEEDED, // destination return 2xx (terminal)
    DEAD, // permanent failure or attempts exhausted (terminal)
}