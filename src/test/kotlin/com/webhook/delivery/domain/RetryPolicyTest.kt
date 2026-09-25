package com.webhook.delivery.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Duration
import kotlin.random.Random

class RetryPolicyTest {

    private val policy = RetryPolicy(
        maxAttempts = 3,
        baseDelay = Duration.ofSeconds(1),
        maxDelay = Duration.ofSeconds(10),
        random = Random(42),   // seeded, so results are reproducible
    )

    @ParameterizedTest
    @ValueSource(ints = [200, 201, 202, 204])
    fun `2xx responses succeed`(status: Int) {
        assertThat(policy.decide(1, Outcome.Response(status))).isEqualTo(Decision.Succeed)
    }

    @ParameterizedTest
    @ValueSource(ints = [408, 429, 500, 502, 503, 504])
    fun `transient statuses are retried`(status: Int) {
        assertThat(policy.decide(1, Outcome.Response(status))).isInstanceOf(Decision.Retry::class.java)
    }

    @ParameterizedTest
    @ValueSource(ints = [301, 400, 401, 403, 404, 410, 422])
    fun `permanent failures give up immediately`(status: Int) {
        assertThat(policy.decide(1, Outcome.Response(status))).isEqualTo(Decision.GiveUp)
    }

    @Test
    fun `network errors are retried`() {
        assertThat(policy.decide(1, Outcome.Error("timeout"))).isInstanceOf(Decision.Retry::class.java)
    }

    @Test
    fun `gives up once max attempts are reached`() {
        assertThat(policy.decide(3, Outcome.Response(503))).isEqualTo(Decision.GiveUp)
    }

    @Test
    fun `success on the last attempt still succeeds`() {
        assertThat(policy.decide(3, Outcome.Response(200))).isEqualTo(Decision.Succeed)
    }

    @Test
    fun `backoff stays within the exponential ceiling and the cap`() {
        val expectedCeilingsMs = mapOf(1 to 1_000L, 2 to 2_000L, 3 to 4_000L, 4 to 8_000L, 5 to 10_000L, 10 to 10_000L)
        expectedCeilingsMs.forEach { (attempt, ceilingMs) ->
            repeat(200) {
                assertThat(policy.backoff(attempt).toMillis()).isBetween(0L, ceilingMs)
            }
        }
    }
}