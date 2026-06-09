package com.mobilemail.data.security

import kotlin.math.min

object PinLockoutPolicy {
    const val BASE_DELAY_MILLIS = 2_000L
    const val MAX_DELAY_MILLIS = 5 * 60 * 1_000L

    fun delayForAttempts(attempts: Int): Long {
        if (attempts <= 0) return 0L

        val exponent = min(attempts - 1, 30)
        val multiplier = 1L shl exponent
        return (BASE_DELAY_MILLIS * multiplier).coerceAtMost(MAX_DELAY_MILLIS)
    }

    fun remainingDelayMillis(
        attempts: Int,
        lastFailedAttemptAtMillis: Long?,
        nowMillis: Long,
    ): Long {
        val failedAt = lastFailedAttemptAtMillis ?: return 0L
        val elapsed = (nowMillis - failedAt).coerceAtLeast(0L)
        return (delayForAttempts(attempts) - elapsed).coerceAtLeast(0L)
    }
}
