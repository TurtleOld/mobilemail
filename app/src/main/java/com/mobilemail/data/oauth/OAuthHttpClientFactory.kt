package com.mobilemail.data.oauth

import okhttp3.Authenticator
import okhttp3.ConnectionPool
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.util.concurrent.TimeUnit

interface OAuthAccessTokenProvider {
    fun getAccessToken(): String
    fun refreshAccessToken(staleToken: String?): String
}

object OAuthHttpClientFactory {
    private val connectionPool = ConnectionPool(5, 5, TimeUnit.MINUTES)

    val sharedClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .connectionPool(connectionPool)
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .build()
    }

    private fun sharedClient(
        connectTimeoutSeconds: Long,
        readTimeoutSeconds: Long,
        writeTimeoutSeconds: Long,
        retryOnConnectionFailure: Boolean
    ): OkHttpClient = sharedClient.newBuilder()
        .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
        .writeTimeout(writeTimeoutSeconds, TimeUnit.SECONDS)
        .retryOnConnectionFailure(retryOnConnectionFailure)
        .build()

    /**
     * Проверка обновлений с GitHub Releases: до двух последовательных запросов
     * (список релизов, затем manifest-ассет). Таймаут держит только один
     * round-trip — общий бюджет всей операции держит вызывающий код
     * ([com.mobilemail.data.update.GithubReleaseUpdateRepository]) через
     * `withTimeout`, а не этот клиент.
     */
    fun forUpdateCheck(): OkHttpClient = sharedClient(
        connectTimeoutSeconds = 15,
        readTimeoutSeconds = 15,
        writeTimeoutSeconds = 15,
        retryOnConnectionFailure = true
    )

    /** Обновление access token: сервер авторизации может отвечать медленнее GitHub API. */
    fun forTokenRefresh(): OkHttpClient = sharedClient(
        connectTimeoutSeconds = 30,
        readTimeoutSeconds = 30,
        writeTimeoutSeconds = 30,
        retryOnConnectionFailure = true
    )

    /** Отзыв токена при выходе — best-effort, не должен надолго блокировать logout. */
    fun forRevocation(): OkHttpClient = sharedClient(
        connectTimeoutSeconds = 10,
        readTimeoutSeconds = 10,
        writeTimeoutSeconds = 10,
        retryOnConnectionFailure = false
    )

    fun authorizedClient(tokenProvider: OAuthAccessTokenProvider): OkHttpClient =
        sharedClient.newBuilder()
            .addInterceptor(OAuthAuthorizationInterceptor(tokenProvider))
            .authenticator(OAuthRefreshAuthenticator(tokenProvider))
            .build()
}

class OAuthAuthorizationInterceptor(
    private val tokenProvider: OAuthAccessTokenProvider
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header(AUTHORIZATION) != null) {
            return chain.proceed(request)
        }

        return chain.proceed(
            request.newBuilder()
                .header(AUTHORIZATION, bearer(tokenProvider.getAccessToken()))
                .build()
        )
    }
}

class OAuthRefreshAuthenticator(
    private val tokenProvider: OAuthAccessTokenProvider
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= MAX_AUTH_ATTEMPTS) return null

        val staleToken = response.request.header(AUTHORIZATION)
            ?.removePrefix(BEARER_PREFIX)
            ?.takeIf { it != response.request.header(AUTHORIZATION) }

        val refreshedToken = runCatching {
            tokenProvider.refreshAccessToken(staleToken)
        }.getOrNull() ?: return null

        return response.request.newBuilder()
            .header(AUTHORIZATION, bearer(refreshedToken))
            .build()
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private companion object {
        const val MAX_AUTH_ATTEMPTS = 2
    }
}

private const val AUTHORIZATION = "Authorization"
private const val BEARER_PREFIX = "Bearer "

private fun bearer(accessToken: String): String = "$BEARER_PREFIX$accessToken"
