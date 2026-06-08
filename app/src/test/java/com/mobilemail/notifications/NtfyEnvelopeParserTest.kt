package com.mobilemail.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class NtfyEnvelopeParserTest {

    @Test
    fun `parse reads ntfy json lines and filters non-message events`() {
        val body = """
            {"id":"1","event":"open","topic":"topic"}
            {"id":"2","event":"message","topic":"topic","title":"Sender","message":"{\"messageId\":\"m-1\"}"}
        """.trimIndent()

        val events = NtfyEnvelopeParser.parse(body)

        assertEquals(1, events.size)
        assertEquals("2", events.first().id)
        assertEquals("Sender", events.first().title)
    }

    @Test
    fun `parse reads backend events wrapper`() {
        val body = """
            {
              "events": [
                {"id":"1","event":"message","topic":"topic","message":"{\"messageId\":\"m-1\"}"}
              ]
            }
        """.trimIndent()

        val events = NtfyEnvelopeParser.parse(body)

        assertEquals(1, events.size)
        assertEquals("1", events.first().id)
        assertEquals("topic", events.first().topic)
    }

    @Test
    fun `parse reads backend array`() {
        val body = """
            [
              {"id":"1","event":"message","topic":"topic"},
              {"id":"2","event":"keepalive","topic":"topic"}
            ]
        """.trimIndent()

        val events = NtfyEnvelopeParser.parse(body)

        assertEquals(1, events.size)
        assertEquals("1", events.first().id)
    }
}
