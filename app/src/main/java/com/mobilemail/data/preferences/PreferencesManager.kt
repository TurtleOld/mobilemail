package com.mobilemail.data.preferences
import android.util.Log
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
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesManager(private val context: Context) {
    companion object {
        private val SERVER_URL_KEY = stringPreferencesKey("server_url")
        private val SAVED_EMAIL_KEY = stringPreferencesKey("saved_email")
        private val SAVED_ACCOUNT_ID_KEY = stringPreferencesKey("saved_account_id")
        private val SESSION_SAVED_KEY = booleanPreferencesKey("session_saved")
    }

    val serverUrl: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[SERVER_URL_KEY]
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
        }
    }

    suspend fun getSavedSession(): SavedSession? {
        val preferences = context.dataStore.data.first()
        val server = preferences[SERVER_URL_KEY]
        val email = preferences[SAVED_EMAIL_KEY]
        val accountId = preferences[SAVED_ACCOUNT_ID_KEY]
        val isSaved = preferences[SESSION_SAVED_KEY] ?: false

        return if (isSaved && !server.isNullOrBlank() && !email.isNullOrBlank() && !accountId.isNullOrBlank()) {
            SavedSession(server, email, accountId)
        } else {
            null
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

    suspend fun saveOAuthMetadata(server: String, metadata: OAuthServerMetadata) {
        val key = stringPreferencesKey("oauth_metadata_$server")
        val json = JSONObject().apply {
            put("issuer", metadata.issuer)
            put("deviceAuthorizationEndpoint", metadata.deviceAuthorizationEndpoint)
            put("tokenEndpoint", metadata.tokenEndpoint)
            put("authorizationEndpoint", metadata.authorizationEndpoint)
            put("registrationEndpoint", metadata.registrationEndpoint)
            put("introspectionEndpoint", metadata.introspectionEndpoint)
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
            Log.w("PreferencesManager", "No cached OAuth metadata found for server: $server")
            return null
        }

        Log.d("PreferencesManager", "Found cached OAuth metadata for server: $server")
        Log.d("PreferencesManager", "Cached metadata JSON: $jsonStr")

        return try {
            val json = JSONObject(jsonStr)
            val issuer = json.getString("issuer")
            val deviceAuthorizationEndpoint = json.getString("deviceAuthorizationEndpoint")
            val tokenEndpoint = json.getString("tokenEndpoint")
            val authorizationEndpoint = json.optString("authorizationEndpoint", null)
            val registrationEndpoint = json.optString("registrationEndpoint", null)
            val introspectionEndpoint = json.optString("introspectionEndpoint", null)
            val grantTypesSupported = json.optString("grantTypesSupported", "").split(",").filter { it.isNotBlank() }
            val responseTypesSupported = json.optString("responseTypesSupported", null)?.split(",")?.filter { it.isNotBlank() }
            val scopesSupported = json.optString("scopesSupported", null)?.split(",")?.filter { it.isNotBlank() }

            // Validate that critical endpoints are present
            if (deviceAuthorizationEndpoint.isBlank() || tokenEndpoint.isBlank()) {
                Log.w("PreferencesManager", "Cached OAuth metadata is incomplete - missing critical endpoints")
                Log.w("PreferencesManager", "Device auth endpoint: '$deviceAuthorizationEndpoint'")
                Log.w("PreferencesManager", "Token endpoint: '$tokenEndpoint'")
                return null
            }

            Log.d("PreferencesManager", "Cached OAuth metadata is valid and complete")
            OAuthServerMetadata(
                issuer = issuer,
                deviceAuthorizationEndpoint = deviceAuthorizationEndpoint,
                tokenEndpoint = tokenEndpoint,
                authorizationEndpoint = authorizationEndpoint,
                registrationEndpoint = registrationEndpoint,
                introspectionEndpoint = introspectionEndpoint,
                grantTypesSupported = grantTypesSupported,
                responseTypesSupported = responseTypesSupported,
                scopesSupported = scopesSupported
            )
        } catch (e: Exception) {
            Log.e("PreferencesManager", "Error parsing cached OAuth metadata", e)
            null
        }
    }
}

data class SavedSession(
    val server: String,
    val email: String,
    val accountId: String
)
