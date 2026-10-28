package com.mobilemail.data.oauth

/**
 * Единая точка решения «токен обновить не удалось — это временный сбой сети
 * или сервер авторизации точно отверг refresh token». Раньше это решение
 * принималось по-разному в [com.mobilemail.data.jmap.JmapOAuthClient] (любое
 * исключение — терминальный сбой) и в [com.mobilemail.data.sync.TokenRefreshWorker]
 * (весь диапазон 400..499 — терминальный сбой, включая транзиентные 408/429).
 */
sealed class OAuthRefreshFailure {
    data class Transient(val cause: Throwable) : OAuthRefreshFailure()
    data class TerminalAuthFailure(val reason: String, val cause: Throwable) : OAuthRefreshFailure()
}

object OAuthRefreshFailureClassifier {
    private val terminalStatusCodes = setOf(400, 401, 403)

    fun classify(error: Throwable): OAuthRefreshFailure {
        if (error is OAuthException && error.statusCode in terminalStatusCodes) {
            return OAuthRefreshFailure.TerminalAuthFailure(reason = error.message, cause = error)
        }
        return OAuthRefreshFailure.Transient(cause = error)
    }
}
