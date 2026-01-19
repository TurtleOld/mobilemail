package com.mobilemail.data.oauth

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class DeviceCodeResponse(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val verificationUriComplete: String?,
    val expiresIn: Int,
    val interval: Int
)

data class TokenResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Int?,
    val refreshToken: String?
)

sealed class DeviceFlowState {
    object Idle : DeviceFlowState()
    data class WaitingForUser(
        val userCode: String,
        val verificationUri: String,
        val verificationUriComplete: String?,
        val expiresAt: Long
    ) : DeviceFlowState()
    data class Success(val tokenResponse: TokenResponse) : DeviceFlowState()
    data class Error(val error: OAuthDeviceFlowError) : DeviceFlowState()
}

sealed class OAuthDeviceFlowError {
    data class AuthorizationPending(val message: String = "Ожидание авторизации пользователя") : OAuthDeviceFlowError()
    data class SlowDown(val message: String = "Слишком частые запросы, замедление") : OAuthDeviceFlowError()
    data class ExpiredToken(val message: String = "Время ожидания истекло") : OAuthDeviceFlowError()
    data class AccessDenied(val message: String = "Доступ запрещён") : OAuthDeviceFlowError()
    data class NetworkError(val message: String, val cause: Throwable? = null) : OAuthDeviceFlowError()
    data class ServerError(val message: String, val statusCode: Int? = null) : OAuthDeviceFlowError()
    data class UnknownError(val message: String, val cause: Throwable? = null) : OAuthDeviceFlowError()
}

class DeviceFlowClient(
    private val metadata: OAuthServerMetadata,
    private val clientId: String,
    private val client: OkHttpClient
) {
    private var pollingJob: Job? = null
    private val isCancelled = AtomicBoolean(false)
    
    suspend fun requestDeviceCode(scopes: List<String> = listOf(
        "urn:ietf:params:jmap:core",
        "urn:ietf:params:jmap:mail",
        "offline_access"
    )): DeviceCodeResponse = withContext(Dispatchers.IO) {
        val formBody = FormBody.Builder()
            .add("client_id", clientId)
            .apply {
                if (scopes.isNotEmpty()) {
                    add("scope", scopes.joinToString(" "))
                }
            }
            .build()
        
        val request = Request.Builder()
            .url(metadata.deviceAuthorizationEndpoint)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .post(formBody)
            .build()
        
        Log.d("DeviceFlowClient", "Запрос device code: ${metadata.deviceAuthorizationEndpoint}")
        
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: ""
        
        if (!response.isSuccessful) {
            val error = parseErrorResponse(body, response.code)
            throw OAuthException("Ошибка получения device code: ${error.message}", response.code)
        }
        
        try {
            val json = JSONObject(body)
            val deviceCode = json.getString("device_code")
            val userCode = json.getString("user_code")
            val verificationUri = json.getString("verification_uri")
            val verificationUriComplete = json.optString("verification_uri_complete", null)
            val expiresIn = json.getInt("expires_in")
            val interval = json.optInt("interval", 5)
            
            Log.d("DeviceFlowClient", "Device code получен: user_code=$userCode, expires_in=$expiresIn, interval=$interval")
            
            DeviceCodeResponse(
                deviceCode = deviceCode,
                userCode = userCode,
                verificationUri = verificationUri,
                verificationUriComplete = verificationUriComplete.takeIf { !it.isNullOrBlank() },
                expiresIn = expiresIn,
                interval = interval
            )
        } catch (e: Exception) {
            Log.e("DeviceFlowClient", "Ошибка парсинга device code ответа", e)
            throw OAuthException("Ошибка парсинга device code: ${e.message}", null, e)
        }
    }
    
    suspend fun pollForToken(
        deviceCode: String,
        interval: Int,
        expiresAt: Long,
        onStateChange: (DeviceFlowState) -> Unit
    ) {
        isCancelled.set(false)
        var currentInterval = interval * 1000L
        val startTime = System.currentTimeMillis()
        
        pollingJob = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                while (isActive && !isCancelled.get()) {
                    if (System.currentTimeMillis() >= expiresAt) {
                        onStateChange(DeviceFlowState.Error(OAuthDeviceFlowError.ExpiredToken()))
                        return@launch
                    }
                    
                    val tokenResponse = try {
                        requestToken(deviceCode)
                    } catch (e: OAuthException) {
                        val error = parseErrorResponse(e.message ?: "", e.statusCode)
                        when (error) {
                            is OAuthDeviceFlowError.AuthorizationPending -> {
                                delay(currentInterval)
                                continue
                            }
                            is OAuthDeviceFlowError.SlowDown -> {
                                currentInterval += 5000L
                                delay(currentInterval)
                                continue
                            }
                            is OAuthDeviceFlowError.ExpiredToken,
                            is OAuthDeviceFlowError.AccessDenied -> {
                                onStateChange(DeviceFlowState.Error(error))
                                return@launch
                            }
                            else -> {
                                onStateChange(DeviceFlowState.Error(error))
                                return@launch
                            }
                        }
                    }
                    
                    onStateChange(DeviceFlowState.Success(tokenResponse))
                    return@launch
                }
            } catch (e: Exception) {
                if (!isCancelled.get()) {
                    Log.e("DeviceFlowClient", "Ошибка polling", e)
                    onStateChange(DeviceFlowState.Error(
                        OAuthDeviceFlowError.NetworkError("Ошибка polling: ${e.message}", e)
                    ))
                }
            }
        }
    }
    
    private suspend fun requestToken(deviceCode: String): TokenResponse = withContext(Dispatchers.IO) {
        val formBody = FormBody.Builder()
            .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
            .add("device_code", deviceCode)
            .add("client_id", clientId)
            .build()
        
        val request = Request.Builder()
            .url(metadata.tokenEndpoint)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .post(formBody)
            .build()
        
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: ""
        
        if (!response.isSuccessful) {
            val error = parseErrorResponse(body, response.code)
            throw OAuthException("Ошибка получения токена: ${error.message}", response.code)
        }
        
        try {
            val json = JSONObject(body)
            
            if (json.has("error")) {
                val error = parseErrorResponse(body, response.code)
                throw OAuthException(error.message, response.code)
            }
            
            val accessToken = json.getString("access_token")
            val tokenType = json.getString("token_type")
            val expiresIn = json.optInt("expires_in", -1).takeIf { it > 0 }
            val refreshToken = json.optString("refresh_token", null).takeIf { !it.isNullOrBlank() }
            
            Log.d("DeviceFlowClient", "Токен получен: token_type=$tokenType, expires_in=$expiresIn, has_refresh=${refreshToken != null}")
            
            TokenResponse(
                accessToken = accessToken,
                tokenType = tokenType,
                expiresIn = expiresIn,
                refreshToken = refreshToken
            )
        } catch (e: OAuthException) {
            throw e
        } catch (e: Exception) {
            Log.e("DeviceFlowClient", "Ошибка парсинга token ответа", e)
            throw OAuthException("Ошибка парсинга token: ${e.message}", null, e)
        }
    }
    
    private fun parseErrorResponse(body: String, statusCode: Int?): OAuthDeviceFlowError {
        return try {
            val json = JSONObject(body)
            val error = json.optString("error", "")
            
            when (error) {
                "authorization_pending" -> OAuthDeviceFlowError.AuthorizationPending()
                "slow_down" -> OAuthDeviceFlowError.SlowDown()
                "expired_token" -> OAuthDeviceFlowError.ExpiredToken()
                "access_denied" -> OAuthDeviceFlowError.AccessDenied()
                else -> {
                    val errorDescription = json.optString("error_description", "")
                    OAuthDeviceFlowError.ServerError(
                        errorDescription.ifEmpty { "Ошибка сервера: $error" },
                        statusCode
                    )
                }
            }
        } catch (e: Exception) {
            when {
                statusCode != null && statusCode >= 500 -> {
                    OAuthDeviceFlowError.ServerError("Ошибка сервера: код $statusCode", statusCode)
                }
                statusCode != null && statusCode in 400..499 -> {
                    OAuthDeviceFlowError.NetworkError("Ошибка клиента: код $statusCode")
                }
                else -> {
                    OAuthDeviceFlowError.UnknownError("Неизвестная ошибка: ${body.take(200)}", e)
                }
            }
        }
    }
    
    fun cancel() {
        isCancelled.set(true)
        pollingJob?.cancel()
        pollingJob = null
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
