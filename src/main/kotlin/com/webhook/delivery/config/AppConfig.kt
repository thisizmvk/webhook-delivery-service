package com.webhook.delivery.config

import com.webhook.delivery.domain.RetryPolicy
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Clock

@Configuration
class AppConfig {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun retryPolicy(properties: WebhookProperties) = RetryPolicy(
        maxAttempts = properties.maxAttempts,
        baseDelay = properties.baseBackoff,
        maxDelay = properties.maxBackoff,
    )

    @Bean
    fun webhookRestClient(properties: WebhookProperties): RestClient {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(properties.connectTimeout)
            setReadTimeout(properties.readTimeout)
        }
        return RestClient.builder().requestFactory(requestFactory).build()
    }
}