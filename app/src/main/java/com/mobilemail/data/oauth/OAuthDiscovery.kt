package com.mobilemail.data.oauth

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Метаданные OAuth сервера, полученные из discovery endpoint.
 *
 * @param issuer Идентификатор OAuth сервера
 * @param deviceAuthorizationEndpoint URL для запроса device code
 * @param tokenEndpoint URL для получения и обновления токенов
 * @param authorizationEndpoint URL для авторизации (опционально)
 * @param grantTypesSupported Список поддерживаемых grant types
 * @param scopesSupported Список поддерживаемых scopes (опционально)
 */
data class OAuthServerMetadata(
    val issuer: String,
    val deviceAuthorizationEndpoint: String,
    val tokenEndpoint: String,
    val authorizationEndpoint: String?,
    val grantTypesSupported: List<String>,
    val scopesSupported: List<String>?
)

/**
 * Клиент для получения метаданных OAuth сервера из discovery endpoint.
 *
 * Выполняет запрос к `.well-known/oauth-authorization-server` и извлекает
 * необходимые endpoints для OAuth 2.0 Device Authorization Grant flow.
 *
 * @param client HTTP клиент для выполнения запросов
 *
 * @see [OAuthServerMetadata] для структуры метаданных
 * @see [DeviceFlowClient] для использования метаданных
 *
 * @sample com.mobilemail.data.oauth.OAuthDiscoveryExample.discover
 */
class OAuthDiscovery(private val client: OkHttpClient) {
    /**
     * Получает метаданные OAuth сервера из discovery endpoint.
     *
     * @param discoveryUrl URL discovery endpoint (например, `https://mail.example.com/.well-known/oauth-authorization-server`)
     * @return Метаданные OAuth сервера
     * @throws OAuthException если discovery запрос не удался или ответ невалиден
     */
    suspend fun discover(discoveryUrl: String): OAuthServerMetadata {
        return withContext(Dispatchers.IO) {
            val normalizedUrl = discoveryUrl.trimEnd('/')
            val url = if (normalizedUrl.endsWith("/.well-known/oauth-authorization-server")) {
                normalizedUrl
            } else {
                "$normalizedUrl/.well-known/oauth-authorization-server"
            }
            
            Log.d("OAuthDiscovery", "Запрос discovery: $url")
            
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            
            if (!response.isSuccessful) {
                throw OAuthException(
                    "Discovery failed: код ${response.code}, ответ: ${body.take(200)}",
                    response.code
                )
            }
            
            try {
                val json = JSONObject(body)
                
                val issuer = json.getString("issuer")
                val deviceAuthorizationEndpoint = json.getString("device_authorization_endpoint")
                val tokenEndpoint = json.getString("token_endpoint")
                val authorizationEndpoint = json.optString("authorization_endpoint", null)
                
                val grantTypesArray = json.optJSONArray("grant_types_supported")
                val grantTypesSupported = if (grantTypesArray != null) {
                    (0 until grantTypesArray.length()).map { grantTypesArray.getString(it) }
                } else {
                    emptyList()
                }
                
                val scopesArray = json.optJSONArray("scopes_supported")
                val scopesSupported = if (scopesArray != null) {
                    (0 until scopesArray.length()).map { scopesArray.getString(it) }
                } else {
                    null
                }
                
                Log.d("OAuthDiscovery", "Discovery успешен: issuer=$issuer, device_endpoint=$deviceAuthorizationEndpoint")
                
                OAuthServerMetadata(
                    issuer = issuer,
                    deviceAuthorizationEndpoint = deviceAuthorizationEndpoint,
                    tokenEndpoint = tokenEndpoint,
                    authorizationEndpoint = authorizationEndpoint,
                    grantTypesSupported = grantTypesSupported,
                    scopesSupported = scopesSupported
                )
            } catch (e: Exception) {
                Log.e("OAuthDiscovery", "Ошибка парсинга discovery ответа", e)
                throw OAuthException("Ошибка парсинга discovery: ${e.message}", null, e)
            }
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

class OAuthException(
    message: String,
    val statusCode: Int? = null,
    cause: Throwable? = null
) : Exception(message, cause)
