package com.mobilemail.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class CredentialManager(private val context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val encryptedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "credentials",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    fun saveCredentials(server: String, email: String, password: String) {
        encryptedPrefs.edit()
            .putString("server_$email", server)
            .putString("email_$email", email)
            .putString("password_$email", password)
            .apply()
    }
    
    fun getPassword(server: String, email: String): String? {
        return encryptedPrefs.getString("password_$email", null)
    }
    
    fun getServer(email: String): String? {
        return encryptedPrefs.getString("server_$email", null)
    }
    
    fun clearCredentials(email: String) {
        encryptedPrefs.edit()
            .remove("server_$email")
            .remove("email_$email")
            .remove("password_$email")
            .apply()
    }
    
    fun clearAllCredentials() {
        encryptedPrefs.edit().clear().apply()
    }
}
