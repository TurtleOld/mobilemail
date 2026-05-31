package com.mobilemail.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NtfyTopicsTest {

    @Test
    fun `forAccount and accountIdFromTopic are consistent`() {
        val topic = NtfyTopics.forAccount("acc-1")
        assertEquals("acc-1", NtfyTopics.accountIdFromTopic(topic))
    }

    @Test
    fun `accountIdFromTopic returns null for unrelated topic`() {
        assertNull(NtfyTopics.accountIdFromTopic("other-topic-format"))
    }
}
