package com.webhook.delivery.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("webhook")
data class WebhookProperties(
    val connectTimeout: Duration = Duration.ofSeconds(2),
    val readTimeout: Duration = Duration.ofSeconds(5),
    val maxAttempts: Int = 5,
    val baseBackoff: Duration = Duration.ofSeconds(1),
    val maxBackoff: Duration = Duration.ofMinutes(5),
    val batchSize: Int = 50,
)