package com.webhook.delivery.domain

import java.time.Instant

data class Attempt(
    val number: Int,
    val startedAt: Instant,
    val durationMs: Long,
    val httpStatus: Int?,
    val error: String?,
)