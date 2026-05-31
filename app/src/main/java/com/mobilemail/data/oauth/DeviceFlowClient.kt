package com.mobilemail.data.oauth

import android.util.Log
import com.mobilemail.util.LogRedactor
import com.mobilemail.util.optStringOrNull
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
import java.io.IOException
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

sealed class OAuthDeviceFlowError(open val message: String) {
    data class AuthorizationPending(override val message: String = "Ожидание авторизации пользователя") : OAuthDeviceFlowError(message)
    data class SlowDown(override val message: String = "Слишком частые запросы, замедление") : OAuthDeviceFlowError(message)
    data class ExpiredToken(override val message: String = "Время ожидания истекло") : OAuthDeviceFlowError(message)
    data class AccessDenied(override val message: String = "Доступ запрещён") : OAuthDeviceFlowError(message)
    data class NetworkError(override val message: String, val cause: Throwable? = null) : OAuthDeviceFlowError(message)
    data class ServerError(override val message: String, val statusCode: Int? = null) : OAuthDeviceFlowError(message)
    data class UnknownError(override val message: String, val cause: Throwable? = null) : OAuthDeviceFlowError(message)
}

/** Result of a single poll iteration, used to avoid jump statements in the loop. */
private sealed class PollStepResult {
    object Continue : PollStepResult()
    data class Delay(val intervalMs: Long) : PollStepResult()
    data class Terminal(val state: DeviceFlowState) : PollStepResult()
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

        Log.d("DeviceFlowClient", "=== REQUESTING DEVICE CODE ===")
        Log.d("DeviceFlowClient", "Device authorization endpoint: ${LogRedactor.redact(metadata.deviceAuthorizationEndpoint)}")
        Log.d("DeviceFlowClient", "Client ID: $clientId")
        Log.d("DeviceFlowClient", "Scopes: ${scopes.joinToString(", ")}")

        Log.d("DeviceFlowClient", "Executing HTTP request...")
        val response = client.newCall(request).execute()
        Log.d("DeviceFlowClient", "HTTP response received, code: ${response.code}")
        val body = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            val error = parseErrorResponse(body, response.code)
            throw OAuthException("Ошибка получения device code: ${error.message}", response.code, body)
        }

        try {
            val json = JSONObject(body)
            val deviceCode = json.getString("device_code")
            val userCode = json.getString("user_code")
            val verificationUri = json.getString("verification_uri")
            val verificationUriComplete = json.optStringOrNull("verification_uri_complete")
            val expiresIn = json.getInt("expires_in")
            val interval = json.optInt("interval", 5)

            Log.d("DeviceFlowClient", "Device code получен: user_code=[REDACTED], expires_in=$expiresIn, interval=$interval")

            DeviceCodeResponse(
                deviceCode = deviceCode,
                userCode = userCode,
                verificationUri = verificationUri,
                verificationUriComplete = verificationUriComplete,
                expiresIn = expiresIn,
                interval = interval
            )
        } catch (e: Exception) {
            Log.e("DeviceFlowClient", "Ошибка парсинга device code ответа", e)
            throw OAuthException(
                "Ошибка парсинга device code: ${e.message}",
                statusCode = null,
                errorBody = null,
                cause = e
            )
        }
    }

    suspend fun pollForToken(
        deviceCode: String,
        interval: Int,
        expiresAt: Long,
        onStateChange: (DeviceFlowState) -> Unit
    ) {
        Log.d("DeviceFlowClient", "=== STARTING TOKEN POLLING ===")
        Log.d("DeviceFlowClient", "Device code: [REDACTED]")
        Log.d("DeviceFlowClient", "Polling interval: ${interval}s")
        Log.d("DeviceFlowClient", "Expires at: ${expiresAt}")

        isCancelled.set(false)
        var currentInterval = interval * 1000L

        pollingJob = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                while (isActive && !isCancelled.get()) {
                    if (System.currentTimeMillis() >= expiresAt) {
                        Log.w("DeviceFlowClient", "Device code expired, stopping polling")
                        onStateChange(DeviceFlowState.Error(OAuthDeviceFlowError.ExpiredToken()))
                        return@launch
                    }

                    Log.d("DeviceFlowClient", "Polling for token (interval: ${currentInterval / 1000}s)...")
                    val stepResult = executePollStep(deviceCode, currentInterval)

                    when (stepResult) {
                        is PollStepResult.Continue -> { /* nothing, loop will repeat */ }
                        is PollStepResult.Delay -> {
                            currentInterval = stepResult.intervalMs
                            delay(currentInterval)
                        }
                        is PollStepResult.Terminal -> {
                            onStateChange(stepResult.state)
                            return@launch
                        }
                    }
                }
            } catch (e: Exception) {
                if (!isCancelled.get()) {
                    Log.e("DeviceFlowClient", "Fatal polling error", e)
                    onStateChange(DeviceFlowState.Error(
                        OAuthDeviceFlowError.NetworkError("Ошибка polling: ${e.message}", e)
                    ))
                } else {
                    Log.d("DeviceFlowClient", "Polling cancelled by user")
                }
            }
        }
    }

    private suspend fun executePollStep(deviceCode: String, currentInterval: Long): PollStepResult {
        return try {
            val tokenResponse = requestToken(deviceCode)
            Log.d("DeviceFlowClient", "Token polling successful!")
            PollStepResult.Terminal(DeviceFlowState.Success(tokenResponse))
        } catch (e: OAuthException) {
            Log.e(
                "DeviceFlowClient",
                "OAuth error during polling: status=${e.statusCode}, body=${LogRedactor.redact(e.errorBody?.take(200))}"
            )
            val errorBody = e.errorBody ?: e.message
            val error = parseErrorResponse(errorBody, e.statusCode)
            Log.d("DeviceFlowClient", "Token request error: ${error.javaClass.simpleName}")
            handlePollOAuthError(error, currentInterval)
        } catch (e: IOException) {
            Log.w(
                "DeviceFlowClient",
                "Network error during polling: ${LogRedactor.redact(e.message)}, retrying in ${currentInterval / 1000}s"
            )
            PollStepResult.Delay(currentInterval)
        }
    }

    private fun handlePollOAuthError(error: OAuthDeviceFlowError, currentInterval: Long): PollStepResult {
        return when (error) {
            is OAuthDeviceFlowError.AuthorizationPending -> {
                Log.d("DeviceFlowClient", "Authorization pending, waiting ${currentInterval / 1000}s...")
                PollStepResult.Delay(currentInterval)
            }
            is OAuthDeviceFlowError.SlowDown -> {
                Log.w("DeviceFlowClient", "Slow down requested, increasing interval")
                PollStepResult.Delay(currentInterval + 5000L)
            }
            is OAuthDeviceFlowError.ExpiredToken -> {
                Log.e("DeviceFlowClient", "Token expired during polling")
                PollStepResult.Terminal(DeviceFlowState.Error(error))
            }
            is OAuthDeviceFlowError.AccessDenied -> {
                Log.e("DeviceFlowClient", "Access denied during polling")
                PollStepResult.Terminal(DeviceFlowState.Error(error))
            }
            else -> {
                Log.e("DeviceFlowClient", "Other OAuth error during polling: ${LogRedactor.redact(error.message)}")
                PollStepResult.Terminal(DeviceFlowState.Error(error))
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

        val startTs = System.currentTimeMillis()
        Log.d("DeviceFlowClient", "Requesting token from endpoint: ${LogRedactor.redact(metadata.tokenEndpoint)} at=$startTs")
        Log.d("DeviceFlowClient", "Device code: [REDACTED]")

        val response = client.newCall(request).execute()
        val endTs = System.currentTimeMillis()
        Log.d("DeviceFlowClient", "Token HTTP response code=${response.code} duration=${endTs - startTs}ms")
        val body = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            val error = parseErrorResponse(body, response.code)
            throw OAuthException("Ошибка получения токена: ${error.message}", response.code, body)
        }

        try {
            val json = JSONObject(body)

            if (json.has("error")) {
                val error = parseErrorResponse(body, response.code)
                throw OAuthException(error.message, response.code, body)
            }

            val accessToken = json.getString("access_token")
            val tokenType = json.getString("token_type")
            val expiresIn = json.optInt("expires_in", -1).takeIf { it > 0 }
            val refreshToken = json.optStringOrNull("refresh_token")

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
            throw OAuthException(
                "Ошибка парсинга token: ${e.message}",
                statusCode = null,
                errorBody = null,
                cause = e
            )
        }
    }

    private fun parseErrorResponse(body: String, statusCode: Int?): OAuthDeviceFlowError {
        val normalizedBody = body.lowercase()
        val quickMatch = checkWellKnownErrors(normalizedBody)
        if (quickMatch != null) return quickMatch

        return try {
            val json = JSONObject(body)
            val error = json.optString("error", "")
            parseJsonError(error, json, statusCode)
        } catch (e: Exception) {
            parseHttpStatusError(statusCode, body, e)
        }
    }

    private fun checkWellKnownErrors(normalizedBody: String): OAuthDeviceFlowError? {
        return when {
            "authorization_pending" in normalizedBody -> OAuthDeviceFlowError.AuthorizationPending()
            "slow_down" in normalizedBody -> OAuthDeviceFlowError.SlowDown()
            "expired_token" in normalizedBody -> OAuthDeviceFlowError.ExpiredToken()
            "access_denied" in normalizedBody -> OAuthDeviceFlowError.AccessDenied()
            else -> null
        }
    }

    private fun parseJsonError(error: String, json: JSONObject, statusCode: Int?): OAuthDeviceFlowError {
        return when (error) {
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
    }

    private fun parseHttpStatusError(statusCode: Int?, body: String, cause: Exception): OAuthDeviceFlowError {
        return when {
            statusCode != null && statusCode >= 500 ->
                OAuthDeviceFlowError.ServerError("Ошибка сервера: код $statusCode", statusCode)
            statusCode != null && statusCode in 400..499 ->
                OAuthDeviceFlowError.NetworkError("Ошибка клиента: код $statusCode")
            else ->
                OAuthDeviceFlowError.UnknownError("Неизвестная ошибка: ${LogRedactor.redact(body.take(200))}", cause)
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
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }
    }
}
