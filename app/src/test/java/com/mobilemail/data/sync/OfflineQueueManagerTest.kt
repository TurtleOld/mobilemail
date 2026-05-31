package com.mobilemail.data.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class OfflineQueueManagerTest {

    @Test
    fun `shouldQueue returns true for transient io errors`() {
        assertTrue(OfflineQueueManager.shouldQueue(IOException("timeout")))
    }

    @Test
    fun `shouldQueue returns false for non network errors`() {
        assertFalse(OfflineQueueManager.shouldQueue(IllegalStateException("bad state")))
    }

    @Test
    fun `resolveNextStatus returns failed below retry limit for network errors`() {
        val status = OfflineQueueManager.resolveNextStatus(
            operationType = OfflineQueueManager.TYPE_MOVE,
            nextAttemptCount = 2,
            error = IOException("temporary")
        )

        assertEquals(OfflineQueueManager.STATUS_FAILED, status)
    }

    @Test
    fun `resolveNextStatus returns permanent_failed when retry limit reached`() {
        val status = OfflineQueueManager.resolveNextStatus(
            operationType = OfflineQueueManager.TYPE_TOGGLE_STAR,
            nextAttemptCount = 4,
            error = IOException("still failing")
        )

        assertEquals(OfflineQueueManager.STATUS_PERMANENT_FAILED, status)
    }

    @Test
    fun `resolveNextStatus returns permanent_failed for non queueable errors`() {
        val status = OfflineQueueManager.resolveNextStatus(
            operationType = OfflineQueueManager.TYPE_SEND,
            nextAttemptCount = 1,
            error = IllegalArgumentException("validation")
        )

        assertEquals(OfflineQueueManager.STATUS_PERMANENT_FAILED, status)
    }
}
