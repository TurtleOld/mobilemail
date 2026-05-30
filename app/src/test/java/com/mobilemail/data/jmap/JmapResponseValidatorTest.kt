package com.mobilemail.data.jmap

import org.json.JSONObject
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.fail

class JmapResponseValidatorTest {

    @Test
    fun `ensureNoMethodError returns original response when there are no method errors`() {
        val response = JSONObject(
            """
            {
              "methodResponses": [
                ["Email/query", {"ids": ["m-1"], "position": 0}, "0"],
                ["Email/get", {"list": []}, "1"]
              ],
              "sessionState": "state-1"
            }
            """.trimIndent()
        )

        val validated = JmapResponseValidator.ensureNoMethodError(response)

        assertSame(response, validated)
    }

    @Test
    fun `ensureNoMethodError returns original response when methodResponses is absent`() {
        val response = JSONObject("""{"sessionState":"state-1"}""")

        val validated = JmapResponseValidator.ensureNoMethodError(response)

        assertSame(response, validated)
    }

    @Test
    fun `ensureNoMethodError throws with JMAP error type and description`() {
        val response = JSONObject(
            """
            {
              "methodResponses": [
                ["Email/query", {"ids": []}, "0"],
                ["error", {"type": "accountNotFound", "description": "Account is unavailable"}, "1"]
              ]
            }
            """.trimIndent()
        )

        try {
            JmapResponseValidator.ensureNoMethodError(response)
            fail("Expected JMAP method error")
        } catch (error: Exception) {
            assertTrue(error.message.orEmpty().contains("accountNotFound"))
            assertTrue(error.message.orEmpty().contains("Account is unavailable"))
        }
    }

    @Test
    fun `ensureNoMethodError uses fallback message for empty error payload`() {
        val response = JSONObject(
            """
            {
              "methodResponses": [
                ["error", {}, "0"]
              ]
            }
            """.trimIndent()
        )

        try {
            JmapResponseValidator.ensureNoMethodError(response)
            fail("Expected JMAP method error")
        } catch (error: Exception) {
            assertTrue(error.message.orEmpty().contains("unknown"))
            assertTrue(error.message.orEmpty().contains("JMAP method error"))
        }
    }
}
