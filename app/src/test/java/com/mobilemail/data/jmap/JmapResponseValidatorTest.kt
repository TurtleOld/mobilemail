package com.mobilemail.data.jmap

import org.json.JSONObject
import org.junit.Test

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

        assertCondition(response === validated, "Expected the original response instance")
    }

    @Test
    fun `ensureNoMethodError returns original response when methodResponses is absent`() {
        val response = JSONObject("""{"sessionState":"state-1"}""")

        val validated = JmapResponseValidator.ensureNoMethodError(response)

        assertCondition(response === validated, "Expected the original response instance")
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
            throw AssertionError("Expected JMAP method error")
        } catch (error: Exception) {
            assertCondition(error.message.orEmpty().contains("accountNotFound"))
            assertCondition(error.message.orEmpty().contains("Account is unavailable"))
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
            throw AssertionError("Expected JMAP method error")
        } catch (error: Exception) {
            assertCondition(error.message.orEmpty().contains("unknown"))
            assertCondition(error.message.orEmpty().contains("JMAP method error"))
        }
    }

    private fun assertCondition(condition: Boolean, message: String = "Expected condition to be true") {
        if (!condition) throw AssertionError(message)
    }
}
