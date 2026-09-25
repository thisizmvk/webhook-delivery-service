package com.webhook.delivery.domain

import java.time.Duration
import kotlin.math.min
import kotlin.random.Random

/** What happened when we tried to deliver. */
sealed interface Outcome {
    data class Response(val status: Int) : Outcome
    data class Error(val message: String) : Outcome   // timeout, connection refused, DNS failure...
}

/** What to do next. */
sealed interface Decision {
    data object Succeed : Decision
    data class Retry(val after: Duration) : Decision
    data object GiveUp : Decision
}

class RetryPolicy(
    val maxAttempts: Int,
    private val baseDelay: Duration,
    private val maxDelay: Duration,
    private val random: Random = Random.Default,
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be at least 1" }
        require(!baseDelay.isNegative && !maxDelay.isNegative) { "delays must not be negative" }
    }

    fun decide(attemptNumber: Int, outcome: Outcome): Decision = when {
        outcome is Outcome.Response && outcome.status in 200..299 -> Decision.Succeed
        !isRetryable(outcome) -> Decision.GiveUp
        attemptNumber >= maxAttempts -> Decision.GiveUp
        else -> Decision.Retry(backoff(attemptNumber))
    }

    /**
     * Exponential backoff with "full jitter": a random delay between 0 and
     * min(maxDelay, baseDelay * 2^(attempt - 1)). Jitter spreads retries out so a
     * recovering destination isn't hit by every retry at the same moment.
     */
    fun backoff(attemptNumber: Int): Duration {
        val exponent = (attemptNumber - 1).coerceIn(0, 20)
        val ceilingMs = min(maxDelay.toMillis(), baseDelay.toMillis() * (1L shl exponent))
        return Duration.ofMillis(random.nextLong(0, ceilingMs + 1))
    }

    private fun isRetryable(outcome: Outcome): Boolean = when (outcome) {
        is Outcome.Error -> true
        is Outcome.Response -> outcome.status == 408 || outcome.status == 429 || outcome.status >= 500
    }
}