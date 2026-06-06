package com.mobilemail.notifications

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import com.mobilemail.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class NtfyEnvelope(
    val id: String,
    val event: String,
    val topic: String,
    val title: String?,
    val message: String?
)

class NtfyClient(context: Context) {
    private val httpClient = OkHttpClient()
    private val preferences = context.getSharedPreferences("ntfy_cursor", Context.MODE_PRIVATE)

    suspend fun fetchPendingMessages(topic: String): List<NtfyEnvelope> = withContext(Dispatchers.IO) {
        val lastMessageId = preferences.getString(cursorKey(topic), null)
        val since = lastMessageId ?: "latest"
        val request = Request.Builder()
            .url(buildUrl(topic, since))
            .header("Authorization", "Bearer ${BuildConfig.NTFY_TOKEN}")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("ntfy fetch failed: ${response.code}")
            }

            val body = response.body?.string().orEmpty()
            val events = body
                .lineSequence()
                .mapNotNull { parseEnvelope(it) }
                .filter { it.event == "message" }
                .toList()

            events.lastOrNull()?.id?.let { saveCursor(topic, it) }
            events
        }
    }

    private fun buildUrl(topic: String, since: String): String {
        val baseUrl = BuildConfig.NTFY_URL.trimEnd('/')
        val encodedTopic = Uri.encode(topic)
        val encodedSince = Uri.encode(since)
        return "$baseUrl/$encodedTopic/json?poll=1&since=$encodedSince"
    }

    private fun parseEnvelope(line: String): NtfyEnvelope? {
        if (line.isBlank()) return null
        return runCatching {
            val json = JSONObject(line)
            NtfyEnvelope(
                id = json.optString("id").trim(),
                event = json.optString("event").trim(),
                topic = json.optString("topic").trim(),
                title = json.optString("title").trim().takeIf { it.isNotEmpty() },
                message = json.optString("message").trim().takeIf { it.isNotEmpty() }
            )
        }.getOrNull()
    }

    private fun saveCursor(topic: String, messageId: String) {
        preferences.edit { putString(cursorKey(topic), messageId) }
    }

    private fun cursorKey(topic: String): String {
        return "cursor_$topic"
    }
}
