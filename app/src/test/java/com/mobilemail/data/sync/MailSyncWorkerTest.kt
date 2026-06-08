package com.mobilemail.data.sync

import androidx.work.ListenableWorker
import org.junit.Assert.assertEquals
import org.junit.Test

class MailSyncWorkerTest {

    @Test
    fun `resolveResult retries when at least one account fails`() {
        val result = MailSyncWorker.resolveResult(
            MailSyncSummary(syncedCount = 1, failedCount = 1, skippedCount = 0)
        )

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun `resolveResult succeeds when accounts sync or skip without failures`() {
        val result = MailSyncWorker.resolveResult(
            MailSyncSummary(syncedCount = 1, failedCount = 0, skippedCount = 1)
        )

        assertEquals(ListenableWorker.Result.success(), result)
    }
}
