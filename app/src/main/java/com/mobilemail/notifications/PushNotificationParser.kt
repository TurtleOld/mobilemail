package com.mobilemail.notifications

import org.json.JSONObject

object PushNotificationParser {
    private val messageIdKeys = listOf("messageId", "message_id", "emailId", "email_id", "mailId", "mail_id")
    private val serverKeys = listOf("server", "serverUrl", "server_url", "baseUrl", "base_url")
    private val emailKeys = listOf("email", "userEmail", "user_email")
    private val accountIdKeys = listOf("accountId", "account_id")

    fun fromRawPayload(rawPayload: String?): PushMessageTarget? {
        if (rawPayload.isNullOrBlank()) return null
        return runCatching {
            val root = JSONObject(rawPayload)
            val payloadData = extractPayloadData(root)
            val messageId = payloadData?.firstNonBlank(messageIdKeys)
                ?: root.firstNonBlank(messageIdKeys)
                ?: return null

            PushMessageTarget(
                messageId = messageId,
                server = payloadData?.firstNonBlank(serverKeys) ?: root.firstNonBlank(serverKeys),
                email = payloadData?.firstNonBlank(emailKeys) ?: root.firstNonBlank(emailKeys),
                accountId = payloadData?.firstNonBlank(accountIdKeys) ?: root.firstNonBlank(accountIdKeys)
            )
        }.getOrNull()
    }

    private fun extractPayloadData(root: JSONObject): JSONObject? {
        val directData = root.optJSONObject("additionalData")
            ?: root.optJSONObject("data")
        if (directData != null) return directData

        val customData = root.optJSONObject("custom")
        return customData?.optJSONObject("a")
    }

    private fun JSONObject.firstNonBlank(keys: List<String>): String? {
        for (key in keys) {
            val value = optStringOrNull(key)
            if (!value.isNullOrBlank()) {
                return value
            }
        }
        return null
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).trim().takeIf { it.isNotEmpty() }
    }
}
