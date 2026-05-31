package com.mobilemail.data.oauth

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.mobilemail.util.LogRedactor
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
 * @param registrationEndpoint URL для регистрации клиента (опционально)
 * @param introspectionEndpoint URL для интроспекции токена (опционально)
 * @param grantTypesSupported Список поддерживаемых grant types
 * @param responseTypesSupported Список поддерживаемых response types (опционально)
 * @param scopesSupported Список поддерживаемых scopes (опционально)
 */
data class OAuthServerMetadata(
    val issuer: String,
    val deviceAuthorizationEndpoint: String,
    val tokenEndpoint: String,
    val authorizationEndpoint: String?,
    val registrationEndpoint: String?,
    val introspectionEndpoint: String?,
    val grantTypesSupported: List<String>,
    val responseTypesSupported: List<String>?,
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
     * @param serverUrl Базовый URL сервера (например, `https://mail.example.com`)
     * @return Метаданные OAuth сервера
     * @throws OAuthException если discovery запрос не удался или ответ невалиден
     */
    @Suppress("ThrowsCount")
    suspend fun discover(serverUrl: String): OAuthServerMetadata {
        Log.d("OAuthDiscovery", "=== STARTING OAuth DISCOVERY ===")
        Log.d("OAuthDiscovery", "Input server URL: $serverUrl")

        return withContext(Dispatchers.IO) {
            Log.d("OAuthDiscovery", "Inside coroutine, starting discovery process")
            val normalizedUrl = serverUrl.trimEnd('/')
            Log.d("OAuthDiscovery", "Normalized URL: $normalizedUrl")

            val urlsToTry = buildDiscoveryUrls(normalizedUrl)
            Log.d("OAuthDiscovery", "Discovery endpoints to try:")
            urlsToTry.forEach { Log.d("OAuthDiscovery", "  - $it") }

            var lastException: Exception? = null

            for (url in urlsToTry) {
                val result = tryDiscoveryUrl(url)
                when {
                    result.metadata != null -> return@withContext result.metadata
                    result.terminalError != null -> {
                        lastException = result.terminalError
                        break
                    }
                    else -> lastException = result.retryableError
                }
            }

            throw OAuthException(
                "Не удалось выполнить discovery запрос ни для одного endpoint'а: ${lastException?.message}",
                statusCode = null,
                cause = lastException
            )
        }
    }

    private fun buildDiscoveryUrls(normalizedUrl: String): List<String> = listOf(
        "$normalizedUrl/.well-known/openid-configuration",
        "$normalizedUrl/.well-known/oauth-authorization-server"
    )

    private data class UrlAttemptResult(
        val metadata: OAuthServerMetadata? = null,
        val retryableError: Exception? = null,
        val terminalError: Exception? = null
    )

    private suspend fun tryDiscoveryUrl(url: String): UrlAttemptResult {
        Log.d("OAuthDiscovery", "Запрос discovery: $url")
        val request = buildDiscoveryRequest(url)
        var lastException: Exception? = null

        repeat(3) { attempt ->
            try {
                val metadata = attemptSingleRequest(request, attempt)
                return UrlAttemptResult(metadata = metadata)
            } catch (e: java.io.EOFException) {
                Log.w("OAuthDiscovery", "EOFException при выполнении запроса, попытка ${attempt + 1}/3")
                lastException = e
                if (attempt < 2) kotlinx.coroutines.delay((attempt + 1) * 1000L)
            } catch (e: java.io.IOException) {
                Log.w("OAuthDiscovery", "IOException при выполнении запроса, попытка ${attempt + 1}/3: ${e.message}")
                lastException = e
                if (attempt < 2) kotlinx.coroutines.delay((attempt + 1) * 1000L)
            } catch (e: Exception) {
                Log.w("OAuthDiscovery", "Ошибка при выполнении запроса, попытка ${attempt + 1}/3: ${e.message}")
                lastException = e
                if (attempt < 2) kotlinx.coroutines.delay((attempt + 1) * 1000L)
            }
        }

        return UrlAttemptResult(retryableError = lastException)
    }

    private fun buildDiscoveryRequest(url: String): Request = Request.Builder()
        .url(url)
        .header("Accept", "application/json")
        .get()
        .build()

    private suspend fun attemptSingleRequest(request: Request, attempt: Int): OAuthServerMetadata {
        val response = client.newCall(request).execute()
        val body = try {
            response.body?.string() ?: ""
        } catch (e: java.io.EOFException) {
            Log.w("OAuthDiscovery", "EOFException при чтении ответа, попытка $attempt")
            if (attempt < 2) {
                kotlinx.coroutines.delay((attempt + 1) * 1000L)
                throw e
            } else {
                ""
            }
        }

        if (!response.isSuccessful) {
            throw OAuthException(
                "Discovery failed: код ${response.code}, ответ: ${LogRedactor.redact(body.take(200))}",
                response.code
            )
        }

        return parseDiscoveryResponse(body)
    }

    private fun parseDiscoveryResponse(body: String): OAuthServerMetadata {
        try {
            Log.d("OAuthDiscovery", "Raw discovery response body: ${LogRedactor.redact(body.take(1000))}")
            val json = JSONObject(body)
            Log.d("OAuthDiscovery", "Parsed JSON keys: ${json.keys().asSequence().toList()}")

            validateRequiredFields(json)

            val issuer = json.getString("issuer")
            val deviceAuthorizationEndpoint = json.getString("device_authorization_endpoint")
            val tokenEndpoint = json.getString("token_endpoint")
            val authorizationEndpoint = json.optNonBlankString("authorization_endpoint")
            val registrationEndpoint = json.optNonBlankString("registration_endpoint")
            val introspectionEndpoint = json.optNonBlankString("introspection_endpoint")
            val grantTypesSupported = json.optJsonArrayToList("grant_types_supported") ?: emptyList()
            val responseTypesSupported = json.optJsonArrayToList("response_types_supported")
            val scopesSupported = json.optJsonArrayToList("scopes_supported")

            Log.d("OAuthDiscovery", "Discovery successful:")
            Log.d("OAuthDiscovery", LogRedactor.redact("  Issuer: $issuer"))
            Log.d("OAuthDiscovery", LogRedactor.redact("  Device Auth Endpoint: $deviceAuthorizationEndpoint"))
            Log.d("OAuthDiscovery", LogRedactor.redact("  Token Endpoint: $tokenEndpoint"))
            Log.d("OAuthDiscovery", LogRedactor.redact("  Authorization Endpoint: $authorizationEndpoint"))
            Log.d("OAuthDiscovery", "  Grant Types: ${grantTypesSupported.joinToString(", ")}")

            return OAuthServerMetadata(
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
            Log.e("OAuthDiscovery", "Ошибка парсинга discovery ответа", e)
            throw OAuthException(
                "Ошибка парсинга discovery: ${e.message}",
                statusCode = null,
                errorBody = null,
                cause = e
            )
        }
    }

    private fun validateRequiredFields(json: JSONObject) {
        val hasIssuer = json.has("issuer")
        val hasDeviceAuthEndpoint = json.has("device_authorization_endpoint")
        val hasTokenEndpoint = json.has("token_endpoint")
        Log.d(
            "OAuthDiscovery",
            "Required fields check - issuer: $hasIssuer, device_auth: $hasDeviceAuthEndpoint, token: $hasTokenEndpoint"
        )
        if (!hasIssuer || !hasDeviceAuthEndpoint || !hasTokenEndpoint) {
            Log.e("OAuthDiscovery", "Discovery response missing required fields!")
            Log.e("OAuthDiscovery", "Available fields: ${json.keys().asSequence().toList()}")
            throw OAuthException("Discovery response missing required OAuth endpoints", null)
        }
    }

    private fun JSONObject.optNonBlankString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val value = getString(key)
        return if (value.isNotBlank()) value else null
    }

    private fun JSONObject.optJsonArrayToList(key: String): List<String>? {
        val array = optJSONArray(key) ?: return null
        return (0 until array.length()).map { array.getString(it) }
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

class OAuthException(
    override val message: String,
    val statusCode: Int? = null,
    val errorBody: String? = null,
    cause: Throwable? = null
) : Exception(message, cause)
