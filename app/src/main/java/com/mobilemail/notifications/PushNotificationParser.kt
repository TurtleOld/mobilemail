package com.mobilemail.notifications

import android.content.Intent
import android.net.Uri
import org.json.JSONObject

data class PushPayload(
    val target: PushMessageTarget,
    val subject: String? = null,
    val fromName: String? = null
)

object PushNotificationParser {
    const val EXTRA_MESSAGE_ID = "push_message_id"
    const val EXTRA_ACCOUNT_ID = "push_account_id"
    const val EXTRA_SERVER = "push_server"
    const val EXTRA_EMAIL = "push_email"

    private val messageIdKeys = listOf("messageId", "message_id", "emailId", "email_id", "mailId", "mail_id")
    private val serverKeys = listOf("server", "serverUrl", "server_url", "baseUrl", "base_url")
    private val emailKeys = listOf("email", "userEmail", "user_email")
    private val accountIdKeys = listOf("accountId", "account_id")
    private val subjectKeys = listOf("subject", "title")
    private val fromNameKeys = listOf("fromName", "from", "senderName", "sender")

    fun fromPayloadJson(rawPayload: String?, fallbackAccountId: String? = null): PushPayload? {
        if (rawPayload.isNullOrBlank()) return null
        return runCatching {
            val root = JSONObject(rawPayload)
            val messageId = root.firstNonBlank(messageIdKeys) ?: return null
            val accountId = root.firstNonBlank(accountIdKeys) ?: fallbackAccountId

            PushPayload(
                target = PushMessageTarget(
                    messageId = messageId,
                    server = root.firstNonBlank(serverKeys),
                    email = root.firstNonBlank(emailKeys),
                    accountId = accountId
                ),
                subject = root.firstNonBlank(subjectKeys),
                fromName = root.firstNonBlank(fromNameKeys)
            )
        }.getOrNull()
    }

    fun fromIntent(intent: Intent?): PushMessageTarget? {
        if (intent == null) return null
        val extras = intent.extras ?: return null
        val messageId = extras.getString(EXTRA_MESSAGE_ID)?.trim().takeIf { !it.isNullOrEmpty() } ?: return null
        val rawServer = extras.getString(EXTRA_SERVER)?.trim().takeIf { !it.isNullOrEmpty() }
        val server = rawServer?.takeIf {
            val scheme = Uri.parse(it).scheme
            scheme == "https" || scheme == "http"
        }
        return PushMessageTarget(
            messageId = messageId,
            accountId = extras.getString(EXTRA_ACCOUNT_ID)?.trim().takeIf { !it.isNullOrEmpty() },
            server = server,
            email = extras.getString(EXTRA_EMAIL)?.trim().takeIf { !it.isNullOrEmpty() }
        )
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
