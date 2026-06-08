package com.mobilemail.notifications

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import com.mobilemail.BuildConfig
import com.mobilemail.data.oauth.OAuthDiscovery
import com.mobilemail.data.oauth.OAuthTokenRefresh
import com.mobilemail.data.oauth.TokenStore
import com.mobilemail.data.preferences.PreferencesManager
import com.mobilemail.data.preferences.SavedSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
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
    private val appContext = context.applicationContext
    private val preferences = context.getSharedPreferences("ntfy_cursor", Context.MODE_PRIVATE)
    private val preferencesManager = PreferencesManager(appContext)
    private val tokenStore = TokenStore(appContext)

    suspend fun fetchPendingMessages(topic: String): List<NtfyEnvelope> = withContext(Dispatchers.IO) {
        val lastMessageId = preferences.getString(cursorKey(topic), null)
        val since = lastMessageId ?: "latest"
        val session = resolveSession(topic)
        val accessToken = resolveAccessToken(session)
        val request = Request.Builder()
            .url(buildUrl(topic, since))
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("ntfy fetch failed: ${response.code}")
            }

            val body = response.body?.string().orEmpty()
            val events = NtfyEnvelopeParser.parse(body)

            events.lastOrNull()?.id?.let { saveCursor(topic, it) }
            events
        }
    }

    private fun buildUrl(topic: String, since: String): String {
        val baseUrl = BuildConfig.MOBILE_PUSH_PROXY_URL.trimEnd('/')
        if (baseUrl.isBlank()) {
            error("MOBILE_PUSH_PROXY_URL is not configured")
        }
        val encodedTopic = Uri.encode(topic)
        val encodedSince = Uri.encode(since)
        return "$baseUrl/api/mobile/ntfy/poll?topic=$encodedTopic&since=$encodedSince"
    }

    private suspend fun resolveSession(topic: String): SavedSession {
        val accountId = NtfyTopics.accountIdFromTopic(topic)
            ?: error("Cannot resolve accountId from push topic")
        return preferencesManager.getSavedAccounts()
            .firstOrNull { it.accountId == accountId }
            ?: error("No saved account for push topic")
    }

    private suspend fun resolveAccessToken(session: SavedSession): String {
        val stored = tokenStore.getTokens(session.server, session.email)
        if (stored?.isValid() == true) {
            return stored.accessToken
        }

        val refreshToken = stored?.refreshToken ?: error("No valid OAuth access token for push proxy")
        val metadata = preferencesManager.getOAuthMetadata(session.server)
            ?: OAuthDiscovery(OAuthDiscovery.createClient()).discover(session.server).also { metadata ->
                preferencesManager.saveOAuthMetadata(session.server, metadata)
            }
        val refreshedToken = OAuthTokenRefresh(
            metadata = metadata,
            clientId = BuildConfig.OAUTH_CLIENT_ID,
            client = OAuthTokenRefresh.createClient()
        ).refreshToken(refreshToken)
        tokenStore.saveTokens(session.server, session.email, refreshedToken)
        return refreshedToken.accessToken
    }

    private fun saveCursor(topic: String, messageId: String) {
        preferences.edit { putString(cursorKey(topic), messageId) }
    }

    private fun cursorKey(topic: String): String {
        return "cursor_$topic"
    }
}

internal object NtfyEnvelopeParser {
    fun parse(body: String): List<NtfyEnvelope> {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return emptyList()

        return when {
            trimmed.startsWith("[") -> runCatching { parseArray(JSONArray(trimmed)) }.getOrElse { parseJsonLines(body) }
            isJsonLines(trimmed) -> parseJsonLines(body)
            trimmed.startsWith("{") -> runCatching { parseObjectBody(JSONObject(trimmed)) }
                .getOrElse { parseJsonLines(body) }
                .ifEmpty { parseJsonLines(body) }
            else -> parseJsonLines(body)
        }.filter { it.event == "message" }
    }

    private fun isJsonLines(trimmed: String): Boolean {
        val firstLine = trimmed.lineSequence().firstOrNull()?.trim().orEmpty()
        return trimmed.contains('\n') && firstLine.startsWith("{") && firstLine != "{"
    }

    private fun parseObjectBody(json: JSONObject): List<NtfyEnvelope> {
        val events = json.optJSONArray("events") ?: return parseEnvelope(json)?.let(::listOf).orEmpty()
        return parseArray(events)
    }

    private fun parseArray(array: JSONArray): List<NtfyEnvelope> {
        return buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                parseEnvelope(json)?.let(::add)
            }
        }
    }

    private fun parseJsonLines(body: String): List<NtfyEnvelope> {
        return body
            .lineSequence()
            .mapNotNull { line ->
                if (line.isBlank()) null else runCatching { JSONObject(line) }.getOrNull()
            }
            .mapNotNull { parseEnvelope(it) }
            .toList()
    }

    private fun parseEnvelope(json: JSONObject): NtfyEnvelope? {
        return NtfyEnvelope(
            id = json.optString("id").trim(),
            event = json.optString("event").trim(),
            topic = json.optString("topic").trim(),
            title = json.optString("title").trim().takeIf { it.isNotEmpty() },
            message = json.optString("message").trim().takeIf { it.isNotEmpty() }
        ).takeIf { it.id.isNotBlank() && it.event.isNotBlank() }
    }

}
