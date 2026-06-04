package com.mobilemail.data.preferences
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mobilemail.data.oauth.OAuthServerMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesManager(private val context: Context) {
    companion object {
        private val SERVER_URL_KEY = stringPreferencesKey("server_url")
        private val SAVED_EMAIL_KEY = stringPreferencesKey("saved_email")
        private val SAVED_ACCOUNT_ID_KEY = stringPreferencesKey("saved_account_id")
        private val SESSION_SAVED_KEY = booleanPreferencesKey("session_saved")
        private val SAVED_ACCOUNTS_KEY = stringPreferencesKey("saved_accounts")
        private val NOTIFICATION_PERMISSION_REQUESTED_KEY = booleanPreferencesKey("notification_permission_requested")
        private val BLOCK_REMOTE_CONTENT_KEY = booleanPreferencesKey("block_remote_content")
    }

    private fun signatureKey(server: String, email: String) =
        stringPreferencesKey("signature_${server}_$email")

    val serverUrl: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[SERVER_URL_KEY]
    }

    val savedAccounts: Flow<List<SavedSession>> = context.dataStore.data.map { preferences ->
        parseAccounts(preferences[SAVED_ACCOUNTS_KEY])
    }

    suspend fun saveServerUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[SERVER_URL_KEY] = url
        }
    }

    suspend fun getServerUrl(): String? {
        return context.dataStore.data.first()[SERVER_URL_KEY]
    }

    suspend fun saveSession(server: String, email: String, accountId: String) {
        context.dataStore.edit { preferences ->
            preferences[SERVER_URL_KEY] = server
            preferences[SAVED_EMAIL_KEY] = email
            preferences[SAVED_ACCOUNT_ID_KEY] = accountId
            preferences[SESSION_SAVED_KEY] = true
            val updatedAccounts = parseAccounts(preferences[SAVED_ACCOUNTS_KEY]).toMutableList().apply {
                removeAll { it.server == server && it.email == email }
                add(SavedSession(server, email, accountId))
            }
            preferences[SAVED_ACCOUNTS_KEY] = serializeAccounts(updatedAccounts)
        }
    }

    suspend fun getSavedAccounts(): List<SavedSession> {
        val preferences = context.dataStore.data.first()
        return parseAccounts(preferences[SAVED_ACCOUNTS_KEY])
    }

    suspend fun setActiveSession(session: SavedSession?) {
        context.dataStore.edit { preferences ->
            if (session == null) {
                preferences.remove(SAVED_EMAIL_KEY)
                preferences.remove(SAVED_ACCOUNT_ID_KEY)
                preferences.remove(SERVER_URL_KEY)
                preferences[SESSION_SAVED_KEY] = false
            } else {
                preferences[SERVER_URL_KEY] = session.server
                preferences[SAVED_EMAIL_KEY] = session.email
                preferences[SAVED_ACCOUNT_ID_KEY] = session.accountId
                preferences[SESSION_SAVED_KEY] = true
            }
        }
    }

    suspend fun getSavedSession(): SavedSession? {
        val preferences = context.dataStore.data.first()
        val server = preferences[SERVER_URL_KEY]
        val email = preferences[SAVED_EMAIL_KEY]
        val accountId = preferences[SAVED_ACCOUNT_ID_KEY]
        val isSaved = preferences[SESSION_SAVED_KEY] ?: false

        val hasCredentials = !server.isNullOrBlank() && !email.isNullOrBlank()
        return if (isSaved && hasCredentials && !accountId.isNullOrBlank()) {
            SavedSession(server, email, accountId)
        } else {
            null
        }
    }

    suspend fun isNotificationPermissionRequested(): Boolean {
        return context.dataStore.data.first()[NOTIFICATION_PERMISSION_REQUESTED_KEY] ?: false
    }

    suspend fun markNotificationPermissionRequested() {
        context.dataStore.edit { preferences ->
            preferences[NOTIFICATION_PERMISSION_REQUESTED_KEY] = true
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { preferences ->
            preferences.remove(SAVED_EMAIL_KEY)
            preferences.remove(SAVED_ACCOUNT_ID_KEY)
            preferences.remove(SERVER_URL_KEY)
            preferences[SESSION_SAVED_KEY] = false
        }
    }

    suspend fun removeSavedAccount(server: String, email: String): SavedSession? {
        var nextActive: SavedSession? = null
        context.dataStore.edit { preferences ->
            val updatedAccounts = parseAccounts(preferences[SAVED_ACCOUNTS_KEY])
                .filterNot { it.server == server && it.email == email }
            preferences[SAVED_ACCOUNTS_KEY] = serializeAccounts(updatedAccounts)

            val currentServer = preferences[SERVER_URL_KEY]
            val currentEmail = preferences[SAVED_EMAIL_KEY]
            val removingCurrent = currentServer == server && currentEmail == email
            nextActive = updatedAccounts.lastOrNull()

            if (removingCurrent) {
                if (nextActive != null) {
                    preferences[SERVER_URL_KEY] = nextActive!!.server
                    preferences[SAVED_EMAIL_KEY] = nextActive!!.email
                    preferences[SAVED_ACCOUNT_ID_KEY] = nextActive!!.accountId
                    preferences[SESSION_SAVED_KEY] = true
                } else {
                    preferences.remove(SAVED_EMAIL_KEY)
                    preferences.remove(SAVED_ACCOUNT_ID_KEY)
                    preferences.remove(SERVER_URL_KEY)
                    preferences[SESSION_SAVED_KEY] = false
                }
            }
        }
        return nextActive
    }

    suspend fun clearAllSessions() {
        context.dataStore.edit { preferences ->
            preferences.remove(SAVED_EMAIL_KEY)
            preferences.remove(SAVED_ACCOUNT_ID_KEY)
            preferences.remove(SERVER_URL_KEY)
            preferences.remove(SAVED_ACCOUNTS_KEY)
            preferences[SESSION_SAVED_KEY] = false
        }
    }

    val blockRemoteContent: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[BLOCK_REMOTE_CONTENT_KEY] ?: true
    }

    suspend fun isBlockRemoteContentEnabled(): Boolean {
        return context.dataStore.data.first()[BLOCK_REMOTE_CONTENT_KEY] ?: true
    }

    suspend fun setBlockRemoteContent(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[BLOCK_REMOTE_CONTENT_KEY] = enabled
        }
    }

    fun signature(server: String, email: String): Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[signatureKey(server, email)]
    }

    suspend fun saveSignature(server: String, email: String, signature: String) {
        context.dataStore.edit { preferences ->
            preferences[signatureKey(server, email)] = signature
        }
    }

    suspend fun getSignature(server: String, email: String): String? {
        return context.dataStore.data.first()[signatureKey(server, email)]
    }

    suspend fun saveOAuthMetadata(server: String, metadata: OAuthServerMetadata) {
        val key = stringPreferencesKey("oauth_metadata_$server")
        val json = JSONObject().apply {
            put("issuer", metadata.issuer)
            put("deviceAuthorizationEndpoint", metadata.deviceAuthorizationEndpoint)
            put("tokenEndpoint", metadata.tokenEndpoint)
            put("authorizationEndpoint", metadata.authorizationEndpoint)
            put("registrationEndpoint", metadata.registrationEndpoint)
            put("introspectionEndpoint", metadata.introspectionEndpoint)
            put("revocationEndpoint", metadata.revocationEndpoint)
            put("grantTypesSupported", metadata.grantTypesSupported.joinToString(","))
            put("responseTypesSupported", metadata.responseTypesSupported?.joinToString(","))
            put("scopesSupported", metadata.scopesSupported?.joinToString(","))
        }.toString()
        context.dataStore.edit { preferences ->
            preferences[key] = json
        }
    }

    suspend fun getOAuthMetadata(server: String): OAuthServerMetadata? {
        val key = stringPreferencesKey("oauth_metadata_$server")
        val jsonStr = context.dataStore.data.first()[key] ?: run {
            return null
        }

        return try {
            val json = JSONObject(jsonStr)
            val issuer = json.getString("issuer")
            val deviceAuthorizationEndpoint = json.getString("deviceAuthorizationEndpoint")
            val tokenEndpoint = json.getString("tokenEndpoint")
            val authorizationEndpoint = json.optNullableString("authorizationEndpoint")
            val registrationEndpoint = json.optNullableString("registrationEndpoint")
            val introspectionEndpoint = json.optNullableString("introspectionEndpoint")
            val revocationEndpoint = json.optNullableString("revocationEndpoint")
            val grantTypesSupported = json.optString("grantTypesSupported", "").split(",").filter { it.isNotBlank() }
            val responseTypesSupported = json.optNullableString("responseTypesSupported")?.split(",")?.filter { it.isNotBlank() }
            val scopesSupported = json.optNullableString("scopesSupported")?.split(",")?.filter { it.isNotBlank() }

            // Validate that critical endpoints are present
            if (deviceAuthorizationEndpoint.isBlank() || tokenEndpoint.isBlank()) {
                return null
            }
            OAuthServerMetadata(
                issuer = issuer,
                deviceAuthorizationEndpoint = deviceAuthorizationEndpoint,
                tokenEndpoint = tokenEndpoint,
                authorizationEndpoint = authorizationEndpoint,
                registrationEndpoint = registrationEndpoint,
                introspectionEndpoint = introspectionEndpoint,
                revocationEndpoint = revocationEndpoint,
                grantTypesSupported = grantTypesSupported,
                responseTypesSupported = responseTypesSupported,
                scopesSupported = scopesSupported
            )
        } catch (e: Exception) {
            null
        }
    }
}

data class SavedSession(
    val server: String,
    val email: String,
    val accountId: String
)

private fun parseAccounts(raw: String?): List<SavedSession> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val server = item.optString("server")
                val email = item.optString("email")
                val accountId = item.optString("accountId")
                if (server.isNotBlank() && email.isNotBlank() && accountId.isNotBlank()) {
                    add(SavedSession(server, email, accountId))
                }
            }
        }
    }.getOrDefault(emptyList())
}

private fun serializeAccounts(accounts: List<SavedSession>): String {
    return JSONArray().apply {
        accounts.forEach { account ->
            put(
                JSONObject().apply {
                    put("server", account.server)
                    put("email", account.email)
                    put("accountId", account.accountId)
                }
            )
        }
    }.toString()
}

private fun JSONObject.optNullableString(key: String): String? {
    val value = optString(key, "")
    return value.takeIf { it.isNotBlank() }
}
