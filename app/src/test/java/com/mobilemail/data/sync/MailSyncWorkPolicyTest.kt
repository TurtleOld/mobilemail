package com.mobilemail.data.sync

import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class MailSyncWorkPolicyTest {

    @Test
    fun `unique work names stay stable`() {
        assertEquals("mail-sync-once", MailSyncWorkPolicy.UNIQUE_ONE_TIME)
        assertEquals("mail-sync-periodic", MailSyncWorkPolicy.UNIQUE_PERIODIC)
    }

    @Test
    fun `one time policy coalesces duplicate schedules`() {
        assertEquals(ExistingWorkPolicy.KEEP, MailSyncWorkPolicy.oneTimeExistingWorkPolicy())
    }

    @Test
    fun `periodic policy keeps existing schedule`() {
        assertEquals(
            ExistingPeriodicWorkPolicy.KEEP,
            MailSyncWorkPolicy.periodicExistingWorkPolicy()
        )
    }

    @Test
    fun `one time request requires network and exponential backoff`() {
        val request = MailSyncWorkPolicy.buildOneTimeWorkRequest()

        assertTrue(request.tags.contains(MailSyncWorkPolicy.TAG_ONE_TIME))
        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, request.workSpec.backoffPolicy)
        assertEquals(TimeUnit.SECONDS.toMillis(MailSyncWorkPolicy.BACKOFF_DELAY_SECONDS), request.workSpec.backoffDelayDuration)
    }

    @Test
    fun `periodic request requires network`() {
        val request = MailSyncWorkPolicy.buildPeriodicWorkRequest()

        assertTrue(request.tags.contains(MailSyncWorkPolicy.TAG_PERIODIC))
        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
    }
}
