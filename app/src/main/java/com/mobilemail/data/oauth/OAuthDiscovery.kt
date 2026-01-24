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
    suspend fun discover(serverUrl: String): OAuthServerMetadata {
        Log.d("OAuthDiscovery", "=== STARTING OAuth DISCOVERY ===")
        Log.d("OAuthDiscovery", "Input server URL: $serverUrl")

        return withContext(Dispatchers.IO) {
            Log.d("OAuthDiscovery", "Inside coroutine, starting discovery process")
            val normalizedUrl = serverUrl.trimEnd('/')
            Log.d("OAuthDiscovery", "Normalized URL: $normalizedUrl")

            // Сначала пробуем OpenID Connect discovery, затем OAuth
            val urlsToTry = listOf(
                "$normalizedUrl/.well-known/openid-configuration",
                "$normalizedUrl/.well-known/oauth-authorization-server"
            )
            Log.d("OAuthDiscovery", "Discovery endpoints to try:")
            urlsToTry.forEach { endpoint ->
                Log.d("OAuthDiscovery", "  - $endpoint")
            }

            var lastException: Exception? = null

            for (url in urlsToTry) {
                Log.d("OAuthDiscovery", "Запрос discovery: $url")

                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .get()
                    .build()

                var urlException: Exception? = null
                var shouldBreak = false

                repeat(3) { attempt ->
                    if (shouldBreak) return@repeat

                    try {
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
                                "Discovery failed: код ${response.code}, ответ: ${body.take(200)}",
                                response.code
                            )
                        }

                        try {
                            Log.d("OAuthDiscovery", "Raw discovery response body: ${body.take(1000)}")

                            val json = JSONObject(body)
                            Log.d("OAuthDiscovery", "Parsed JSON keys: ${json.keys().asSequence().toList()}")

                            // Check if required fields exist
                            val hasIssuer = json.has("issuer")
                            val hasDeviceAuthEndpoint = json.has("device_authorization_endpoint")
                            val hasTokenEndpoint = json.has("token_endpoint")

                            Log.d("OAuthDiscovery", "Required fields check - issuer: $hasIssuer, device_auth: $hasDeviceAuthEndpoint, token: $hasTokenEndpoint")

                            if (!hasIssuer || !hasDeviceAuthEndpoint || !hasTokenEndpoint) {
                                Log.e("OAuthDiscovery", "Discovery response missing required fields!")
                                Log.e("OAuthDiscovery", "Available fields: ${json.keys().asSequence().toList()}")
                                throw OAuthException("Discovery response missing required OAuth endpoints", null)
                            }

                            val issuer = json.getString("issuer")
                            val deviceAuthorizationEndpoint = json.getString("device_authorization_endpoint")
                            val tokenEndpoint = json.getString("token_endpoint")
                            val authorizationEndpoint = if (json.has("authorization_endpoint") && !json.isNull("authorization_endpoint")) {
                                val value = json.getString("authorization_endpoint")
                                if (value.isNotBlank()) value else null
                            } else {
                                null
                            }
                            val registrationEndpoint = if (json.has("registration_endpoint") && !json.isNull("registration_endpoint")) {
                                val value = json.getString("registration_endpoint")
                                if (value.isNotBlank()) value else null
                            } else {
                                null
                            }
                            val introspectionEndpoint = if (json.has("introspection_endpoint") && !json.isNull("introspection_endpoint")) {
                                val value = json.getString("introspection_endpoint")
                                if (value.isNotBlank()) value else null
                            } else {
                                null
                            }

                            val grantTypesArray = json.optJSONArray("grant_types_supported")
                            val grantTypesSupported = if (grantTypesArray != null) {
                                (0 until grantTypesArray.length()).map { grantTypesArray.getString(it) }
                            } else {
                                emptyList()
                            }

                            val responseTypesArray = json.optJSONArray("response_types_supported")
                            val responseTypesSupported = if (responseTypesArray != null) {
                                (0 until responseTypesArray.length()).map { responseTypesArray.getString(it) }
                            } else {
                                null
                            }

                            val scopesArray = json.optJSONArray("scopes_supported")
                            val scopesSupported = if (scopesArray != null) {
                                (0 until scopesArray.length()).map { scopesArray.getString(it) }
                            } else {
                                null
                            }

                            Log.d("OAuthDiscovery", "Discovery successful:")
                            Log.d("OAuthDiscovery", "  Issuer: $issuer")
                            Log.d("OAuthDiscovery", "  Device Auth Endpoint: $deviceAuthorizationEndpoint")
                            Log.d("OAuthDiscovery", "  Token Endpoint: $tokenEndpoint")
                            Log.d("OAuthDiscovery", "  Authorization Endpoint: $authorizationEndpoint")
                            Log.d("OAuthDiscovery", "  Grant Types: ${grantTypesSupported.joinToString(", ")}")

                            return@withContext OAuthServerMetadata(
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
                    } catch (e: java.io.EOFException) {
                        Log.w("OAuthDiscovery", "EOFException при выполнении запроса, попытка ${attempt + 1}/3")
                        urlException = e
                        if (attempt < 2) {
                            kotlinx.coroutines.delay((attempt + 1) * 1000L)
                        } else {
                            // После 3 попыток переходим к следующему URL
                            lastException = e
                        }
                    } catch (e: java.io.IOException) {
                        Log.w("OAuthDiscovery", "IOException при выполнении запроса, попытка ${attempt + 1}/3: ${e.message}")
                        urlException = e
                        if (attempt < 2) {
                            kotlinx.coroutines.delay((attempt + 1) * 1000L)
                        } else {
                            // После 3 попыток переходим к следующему URL
                            lastException = e
                        }
                    } catch (e: Exception) {
                        Log.w("OAuthDiscovery", "Ошибка при выполнении запроса, попытка ${attempt + 1}/3: ${e.message}")
                        urlException = e
                        if (attempt < 2) {
                            kotlinx.coroutines.delay((attempt + 1) * 1000L)
                        } else {
                            // После 3 попыток переходим к следующему URL
                            lastException = e
                            shouldBreak = true
                        }
                    }
                }

                // Если успешно обработали этот URL, выходим из внешнего цикла
                if (urlException == null && !shouldBreak) {
                    break
                }
            }

            throw Exception("Не удалось выполнить discovery запрос ни для одного endpoint'а: ${lastException?.message}", lastException)
        }
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
