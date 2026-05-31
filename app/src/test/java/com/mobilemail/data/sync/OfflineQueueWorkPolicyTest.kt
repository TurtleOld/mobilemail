package com.mobilemail.data.sync

import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class OfflineQueueWorkPolicyTest {

    @Test
    fun `unique work names stay stable`() {
        assertEquals("offline-queue-once", OfflineQueueWorkPolicy.UNIQUE_ONE_TIME)
        assertEquals("offline-queue-periodic", OfflineQueueWorkPolicy.UNIQUE_PERIODIC)
    }

    @Test
    fun `one time policy coalesces duplicate schedules`() {
        assertEquals(ExistingWorkPolicy.KEEP, OfflineQueueWorkPolicy.oneTimeExistingWorkPolicy())
    }

    @Test
    fun `periodic policy keeps existing schedule`() {
        assertEquals(
            ExistingPeriodicWorkPolicy.KEEP,
            OfflineQueueWorkPolicy.periodicExistingWorkPolicy()
        )
    }

    @Test
    fun `one time request requires network and exponential backoff`() {
        val request = OfflineQueueWorkPolicy.buildOneTimeWorkRequest()

        assertTrue(request.tags.contains(OfflineQueueWorkPolicy.TAG_ONE_TIME))
        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, request.workSpec.backoffPolicy)
        assertEquals(TimeUnit.SECONDS.toMillis(OfflineQueueWorkPolicy.BACKOFF_DELAY_SECONDS), request.workSpec.backoffDelayDuration)
    }

    @Test
    fun `periodic request requires network`() {
        val request = OfflineQueueWorkPolicy.buildPeriodicWorkRequest()

        assertTrue(request.tags.contains(OfflineQueueWorkPolicy.TAG_PERIODIC))
        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
    }
}
