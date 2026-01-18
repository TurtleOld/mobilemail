package com.mobilemail.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

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
            preferences[SESSION_SAVED_KEY] = false
        }
    }
}

data class SavedSession(
    val server: String,
    val email: String,
    val accountId: String
)
