package com.mobilemail.data.security

import org.junit.Assert.assertEquals
import org.junit.Test

class PinLockoutPolicyTest {
    @Test
    fun `delay grows exponentially and caps at max`() {
        assertEquals(0L, PinLockoutPolicy.delayForAttempts(0))
        assertEquals(2_000L, PinLockoutPolicy.delayForAttempts(1))
        assertEquals(4_000L, PinLockoutPolicy.delayForAttempts(2))
        assertEquals(8_000L, PinLockoutPolicy.delayForAttempts(3))
        assertEquals(PinLockoutPolicy.MAX_DELAY_MILLIS, PinLockoutPolicy.delayForAttempts(20))
    }

    @Test
    fun `remaining delay decreases with elapsed time`() {
        val remainingDelay = PinLockoutPolicy.remainingDelayMillis(
            attempts = 3,
            lastFailedAttemptAtMillis = 10_000L,
            nowMillis = 13_500L,
        )

        assertEquals(4_500L, remainingDelay)
    }

    @Test
    fun `remaining delay is zero when delay elapsed or timestamp absent`() {
        assertEquals(
            0L,
            PinLockoutPolicy.remainingDelayMillis(
                attempts = 2,
                lastFailedAttemptAtMillis = 10_000L,
                nowMillis = 20_000L,
            )
        )
        assertEquals(
            0L,
            PinLockoutPolicy.remainingDelayMillis(
                attempts = 2,
                lastFailedAttemptAtMillis = null,
                nowMillis = 20_000L,
            )
        )
    }
}
