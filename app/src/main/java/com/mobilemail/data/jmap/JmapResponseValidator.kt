package com.mobilemail.data.jmap

import org.json.JSONObject

internal object JmapResponseValidator {
    fun ensureNoMethodError(response: JSONObject): JSONObject {
        val methodResponses = response.optJSONArray("methodResponses") ?: return response
        for (index in 0 until methodResponses.length()) {
            val methodResponse = methodResponses.optJSONArray(index) ?: continue
            val methodName = methodResponse.optString(0)
            if (methodName == "error") {
                val errorPayload = methodResponse.optJSONObject(1)
                val type = errorPayload?.optString("type").orEmpty().ifBlank { "unknown" }
                val description =
                    errorPayload?.optString("description").orEmpty().ifBlank { "JMAP method error" }
                throw Exception("JMAP method error [$type]: $description")
            }
        }
        return response
    }
}
