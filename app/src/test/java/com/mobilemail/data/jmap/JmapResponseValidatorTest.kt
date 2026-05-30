package com.mobilemail.data.jmap

import org.json.JSONObject
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class JmapResponseValidatorTest {

    @Test
    fun `ensureNoMethodError returns response when no error items`() {
        val response = JSONObject(
            """
            {
              "methodResponses": [
                ["Mailbox/get", {"list":[]}, "0"]
              ]
            }
            """.trimIndent()
        )

        val validated = JmapResponseValidator.ensureNoMethodError(response)

        assertSame(response, validated)
    }

    @Test
    fun `ensureNoMethodError throws when error method response exists`() {
        val response = JSONObject(
            """
            {
              "methodResponses": [
                ["error", {"type":"serverFail","description":"temporary failure"}, "0"]
              ]
            }
            """.trimIndent()
        )

        try {
            JmapResponseValidator.ensureNoMethodError(response)
            throw AssertionError("Expected Exception for JMAP method error")
        } catch (error: Exception) {
            assertTrue(error.message.orEmpty().contains("serverFail"))
            assertTrue(error.message.orEmpty().contains("temporary failure"))
        }
    }
}
