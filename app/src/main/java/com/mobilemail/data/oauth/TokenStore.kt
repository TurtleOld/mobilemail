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
        
        val expiresAtDays = expiresAt?.let { (it - System.currentTimeMillis()) / (1000L * 60 * 60 * 24) }
        
        encryptedPrefs.edit()
            .putString("access_token_${server}_$email", tokenResponse.accessToken)
            .putString("token_type_${server}_$email", tokenResponse.tokenType)
            .putLong("expires_at_${server}_$email", expiresAt ?: -1L)
            .putString("refresh_token_${server}_$email", tokenResponse.refreshToken)
            .apply()
        
        Log.d("TokenStore", "Токены сохранены для $email на $server: expires_in=${tokenResponse.expiresIn}s (${expiresAtDays} дней), has_refresh=${tokenResponse.refreshToken != null}")
    }
    
    fun getTokens(server: String, email: String): StoredToken? {
        val accessToken = encryptedPrefs.getString("access_token_${server}_$email", null)
        val tokenType = encryptedPrefs.getString("token_type_${server}_$email", "Bearer")
        val expiresAt = encryptedPrefs.getLong("expires_at_${server}_$email", -1L).takeIf { it > 0 }
        val refreshToken = encryptedPrefs.getString("refresh_token_${server}_$email", null)
        
        return if (accessToken != null) {
            val token = StoredToken(
                accessToken = accessToken,
                tokenType = tokenType ?: "Bearer",
                expiresAt = expiresAt,
                refreshToken = refreshToken
            )
            
            val expiresAtDays = expiresAt?.let { (it - System.currentTimeMillis()) / (1000L * 60 * 60 * 24) }
            val isExpired = token.isExpired()
            Log.d("TokenStore", "Токены загружены для $email на $server: expired=$isExpired, expires_in_days=$expiresAtDays, has_refresh=${refreshToken != null}")
            
            token
        } else {
            Log.d("TokenStore", "Токены не найдены для $email на $server")
            null
        }
    }
    
    fun clearTokens(server: String, email: String) {
        val hadAccessToken = encryptedPrefs.contains("access_token_${server}_$email")
        val hadRefreshToken = encryptedPrefs.contains("refresh_token_${server}_$email")
        
        encryptedPrefs.edit()
            .remove("access_token_${server}_$email")
            .remove("token_type_${server}_$email")
            .remove("expires_at_${server}_$email")
            .remove("refresh_token_${server}_$email")
            .apply()
        
        Log.d("TokenStore", "Токены удалены для $email на $server: had_access=$hadAccessToken, had_refresh=$hadRefreshToken")
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
