package com.mobilemail.data.oauth

import android.content.Context
import android.util.Log
import com.mobilemail.data.security.KeystoreSecureStore

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
    private val secureStore = KeystoreSecureStore(
        context = context,
        prefsName = "oauth_tokens_v2",
        keyAlias = "mobilemail_oauth_tokens_key"
    )
    
    fun saveTokens(server: String, email: String, tokenResponse: TokenResponse) {
        val expiresAt = tokenResponse.expiresIn?.let {
            System.currentTimeMillis() + (it * 1000L)
        }
        
        val expiresAtDays = expiresAt?.let { (it - System.currentTimeMillis()) / (1000L * 60 * 60 * 24) }
        
        secureStore.putString("access_token_${server}_$email", tokenResponse.accessToken)
        secureStore.putString("token_type_${server}_$email", tokenResponse.tokenType)
        secureStore.putLong("expires_at_${server}_$email", expiresAt)
        secureStore.putString("refresh_token_${server}_$email", tokenResponse.refreshToken)
        
        Log.d("TokenStore", "Токены сохранены для $email на $server: expires_in=${tokenResponse.expiresIn}s (${expiresAtDays} дней), has_refresh=${tokenResponse.refreshToken != null}")
    }
    
    fun getTokens(server: String, email: String): StoredToken? {
        val accessToken = secureStore.getString("access_token_${server}_$email")
        val tokenType = secureStore.getString("token_type_${server}_$email") ?: "Bearer"
        val expiresAt = secureStore.getLong("expires_at_${server}_$email")
        val refreshToken = secureStore.getString("refresh_token_${server}_$email")
        
        return if (accessToken != null) {
            val token = StoredToken(
                accessToken = accessToken,
                tokenType = tokenType,
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
        val accessKey = "access_token_${server}_$email"
        val refreshKey = "refresh_token_${server}_$email"
        val hadAccessToken = secureStore.contains(accessKey)
        val hadRefreshToken = secureStore.contains(refreshKey)
        secureStore.remove(
            accessKey,
            "token_type_${server}_$email",
            "expires_at_${server}_$email",
            refreshKey
        )
        
        Log.d("TokenStore", "Токены удалены для $email на $server: had_access=$hadAccessToken, had_refresh=$hadRefreshToken")
    }
    
    fun clearAllTokens() {
        secureStore.clear()
        Log.d("TokenStore", "Все токены удалены")
    }
    
    fun hasValidTokens(server: String, email: String): Boolean {
        val tokens = getTokens(server, email)
        return tokens != null && tokens.isValid()
    }
}
