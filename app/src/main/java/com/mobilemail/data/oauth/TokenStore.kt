package com.mobilemail.data.oauth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class StoredToken(
    val accessToken: String,
    val tokenType: String,
    val expiresAt: Long?,
    val refreshToken: String?
) {
    fun isExpired(): Boolean {
        return expiresAt != null && System.currentTimeMillis() >= expiresAt
    }
    
    fun isValid(): Boolean {
        return !isExpired()
    }
}

class TokenStore(private val context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val encryptedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "oauth_tokens",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    fun saveTokens(server: String, email: String, tokenResponse: TokenResponse) {
        val expiresAt = tokenResponse.expiresIn?.let {
            System.currentTimeMillis() + (it * 1000L)
        }
        
        encryptedPrefs.edit()
            .putString("access_token_${server}_$email", tokenResponse.accessToken)
            .putString("token_type_${server}_$email", tokenResponse.tokenType)
            .putLong("expires_at_${server}_$email", expiresAt ?: -1L)
            .putString("refresh_token_${server}_$email", tokenResponse.refreshToken)
            .apply()
        
        Log.d("TokenStore", "Токены сохранены для $email на $server")
    }
    
    fun getTokens(server: String, email: String): StoredToken? {
        val accessToken = encryptedPrefs.getString("access_token_${server}_$email", null)
        val tokenType = encryptedPrefs.getString("token_type_${server}_$email", "Bearer")
        val expiresAt = encryptedPrefs.getLong("expires_at_${server}_$email", -1L).takeIf { it > 0 }
        val refreshToken = encryptedPrefs.getString("refresh_token_${server}_$email", null)
        
        return if (accessToken != null) {
            StoredToken(
                accessToken = accessToken,
                tokenType = tokenType ?: "Bearer",
                expiresAt = expiresAt,
                refreshToken = refreshToken
            )
        } else {
            null
        }
    }
    
    fun clearTokens(server: String, email: String) {
        encryptedPrefs.edit()
            .remove("access_token_${server}_$email")
            .remove("token_type_${server}_$email")
            .remove("expires_at_${server}_$email")
            .remove("refresh_token_${server}_$email")
            .apply()
        
        Log.d("TokenStore", "Токены удалены для $email на $server")
    }
    
    fun clearAllTokens() {
        encryptedPrefs.edit().clear().apply()
        Log.d("TokenStore", "Все токены удалены")
    }
    
    fun hasValidTokens(server: String, email: String): Boolean {
        val tokens = getTokens(server, email)
        return tokens != null && tokens.isValid()
    }
}
