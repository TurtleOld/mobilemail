package com.mobilemail.data.sync

import org.junit.Assert.assertFalse
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
}
