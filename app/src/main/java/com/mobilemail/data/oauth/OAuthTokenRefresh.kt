package com.mobilemail.data.oauth

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class OAuthTokenRefresh(
    private val metadata: OAuthServerMetadata,
    private val clientId: String,
    private val client: OkHttpClient
) {
    suspend fun refreshToken(refreshToken: String): TokenResponse = withContext(Dispatchers.IO) {
        val formBody = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .add("client_id", clientId)
            .build()
        
        val request = Request.Builder()
            .url(metadata.tokenEndpoint)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .post(formBody)
            .build()
        
        Log.d("OAuthTokenRefresh", "Обновление токена: ${metadata.tokenEndpoint}")
        
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: ""
        
        if (!response.isSuccessful) {
            val error = try {
                val json = JSONObject(body)
                json.optString("error_description", json.optString("error", "Unknown error"))
            } catch (e: Exception) {
                body.take(200)
            }
            throw OAuthException("Ошибка обновления токена: $error", response.code)
        }
        
        try {
            val json = JSONObject(body)
            val accessToken = json.getString("access_token")
            val tokenType = json.getString("token_type")
            val expiresIn = json.optInt("expires_in", -1).takeIf { it > 0 }
            val newRefreshToken = json.optString("refresh_token", null).takeIf { !it.isNullOrBlank() } ?: refreshToken
            
            Log.d("OAuthTokenRefresh", "Токен обновлён: token_type=$tokenType, expires_in=$expiresIn")
            
            TokenResponse(
                accessToken = accessToken,
                tokenType = tokenType,
                expiresIn = expiresIn,
                refreshToken = newRefreshToken
            )
        } catch (e: Exception) {
            Log.e("OAuthTokenRefresh", "Ошибка парсинга refresh token ответа", e)
            throw OAuthException("Ошибка парсинга refresh token: ${e.message}", null, e)
        }
    }
    
    companion object {
        fun createClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }
    }
}
