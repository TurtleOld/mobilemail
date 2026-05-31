package com.mobilemail.data.sync

import androidx.work.ListenableWorker
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineQueueWorkerTest {

    @Test
    fun `resolveResult returns retry when summary has failed operations`() {
        val result = OfflineQueueWorker.resolveResult(
            OfflineQueueSummary(
                processedCount = 1,
                failedCount = 2,
                pendingCount = 3
            )
        )

        assertTrue(result is ListenableWorker.Result.Retry)
    }

    @Test
    fun `resolveResult returns success when no failed operations`() {
        val result = OfflineQueueWorker.resolveResult(
            OfflineQueueSummary(
                processedCount = 3,
                failedCount = 0,
                pendingCount = 0
            )
        )

        assertTrue(result is ListenableWorker.Result.Success)
    }
}
