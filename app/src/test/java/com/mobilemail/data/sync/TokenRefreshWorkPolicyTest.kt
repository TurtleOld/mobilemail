package com.mobilemail.data.sync

import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class TokenRefreshWorkPolicyTest {

    @Test
    fun `unique work name stays stable`() {
        assertEquals("token-refresh-periodic", TokenRefreshWorkPolicy.UNIQUE_PERIODIC)
    }

    @Test
    fun `periodic policy keeps existing schedule`() {
        assertEquals(
            ExistingPeriodicWorkPolicy.KEEP,
            TokenRefreshWorkPolicy.periodicExistingWorkPolicy()
        )
    }

    @Test
    fun `periodic request requires network and exponential backoff`() {
        val request = TokenRefreshWorkPolicy.buildPeriodicWorkRequest()

        assertTrue(request.tags.contains(TokenRefreshWorkPolicy.TAG_PERIODIC))
        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, request.workSpec.backoffPolicy)
        assertEquals(TimeUnit.SECONDS.toMillis(TokenRefreshWorkPolicy.BACKOFF_DELAY_SECONDS), request.workSpec.backoffDelayDuration)
    }
}
