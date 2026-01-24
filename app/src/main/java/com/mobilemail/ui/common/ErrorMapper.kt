package com.mobilemail.ui.common
import android.util.Log
import com.mobilemail.data.jmap.TwoFactorRequiredException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

object ErrorMapper {

    fun mapException(exception: Throwable): AppError {
        val root = rootCause(exception)
        val rootMessage = (root.message ?: root.javaClass.simpleName).orEmpty()
        val fullMessage = (exception.message ?: exception.javaClass.simpleName).orEmpty()

        val messageForUi = chooseBestMessage(rootMessage, fullMessage)

        Log.e("ErrorMapper", "Mapping exception to AppError:")
        Log.e("ErrorMapper", "  Root cause: ${root.javaClass.simpleName}: $rootMessage")
        Log.e("ErrorMapper", "  Full exception: ${exception.javaClass.simpleName}: $fullMessage")
        Log.e("ErrorMapper", "  Selected message for UI: $messageForUi")

        return when (root) {

            is TwoFactorRequiredException -> AppError.TwoFactorRequired(
                errorMessage = root.message ?: "Требуется двухфакторная авторизация",
                errorCause = exception
            )

            is SocketTimeoutException -> AppError.NetworkError(
                errorMessage = if (messageForUi.isBlank()) "Timeout" else messageForUi,
                errorCause = exception,
                isTimeout = true
            )

            is UnknownHostException -> AppError.NetworkError(
                errorMessage = "Сервер не найден (DNS). Проверьте адрес сервера и интернет.",
                errorCause = exception,
                isConnectionError = true
            )

            is ConnectException -> AppError.NetworkError(
                errorMessage = "Не удалось подключиться к серверу. Проверьте адрес/порт и доступность сервера.",
                errorCause = exception,
                isConnectionError = true
            )

            is UnknownServiceException -> {
                val msg = rootMessage
                if (msg.contains("CLEARTEXT", ignoreCase = true) ||
                    msg.contains("cleartext", ignoreCase = true)
                ) {
                    AppError.NetworkError(
                        errorMessage = "HTTP запрещён (cleartext). Используйте HTTPS или настройте network security config.",
                        errorCause = exception,
                        isConnectionError = true
                    )
                } else {
                    AppError.NetworkError(
                        errorMessage = if (messageForUi.isBlank()) "Ошибка сетевого сервиса" else messageForUi,
                        errorCause = exception,
                        isConnectionError = true
                    )
                }
            }

            is SSLHandshakeException, is SSLPeerUnverifiedException -> AppError.NetworkError(
                errorMessage = "Проблема TLS/сертификата сервера: ${shorten(rootMessage)}",
                errorCause = exception,
                isConnectionError = true
            )

            is IOException -> {
                val m = rootMessage.lowercase()
                when {
                    m.contains("timeout") -> AppError.NetworkError(
                        errorMessage = "Timeout при подключении к серверу.",
                        errorCause = exception,
                        isTimeout = true
                    )

                    m.contains("refused") || m.contains("connection refused") -> AppError.NetworkError(
                        errorMessage = "Сервер отказал в соединении (connection refused). Проверьте порт/файрвол/прокси.",
                        errorCause = exception,
                        isConnectionError = true
                    )

                    m.contains("reset") || m.contains("connection reset") -> AppError.NetworkError(
                        errorMessage = "Соединение сброшено сервером (connection reset).",
                        errorCause = exception,
                        isConnectionError = true
                    )

                    else -> AppError.NetworkError(
                        errorMessage = if (messageForUi.isBlank()) "Сетевая ошибка: ${shorten(rootMessage)}" else messageForUi,
                        errorCause = exception,
                        isConnectionError = true
                    )
                }
            }

            else -> {
                val combined = (rootMessage + " " + fullMessage).lowercase()
                val statusCode = extractStatusCode(combined)

                when {
                    statusCode == 401 || statusCode == 403 -> AppError.AuthError(
                        errorMessage = if (messageForUi.isBlank()) "Ошибка авторизации ($statusCode)" else messageForUi,
                        errorCause = exception
                    )

                    statusCode == 404 -> AppError.ServerError(
                        errorMessage = if (messageForUi.isBlank()) "Не найдено (404)" else messageForUi,
                        errorCause = exception,
                        statusCode = 404
                    )

                    statusCode != null && statusCode in 500..599 -> AppError.ServerError(
                        errorMessage = if (messageForUi.isBlank()) "Ошибка сервера ($statusCode)" else messageForUi,
                        errorCause = exception,
                        statusCode = statusCode
                    )

                    combined.contains("parse") || combined.contains("json") -> AppError.ParseError(
                        errorMessage = if (messageForUi.isBlank()) "Ошибка обработки данных (parse/json)" else messageForUi,
                        errorCause = exception
                    )

                    combined.contains("timeout") -> AppError.NetworkError(
                        errorMessage = if (messageForUi.isBlank()) "Timeout" else messageForUi,
                        errorCause = exception,
                        isTimeout = true
                    )

                    combined.contains("connect") -> AppError.NetworkError(
                        errorMessage = if (messageForUi.isBlank()) "Ошибка соединения" else messageForUi,
                        errorCause = exception,
                        isConnectionError = true
                    )

                    else -> AppError.UnknownError(
                        errorMessage = if (messageForUi.isBlank()) shorten(rootMessage) else messageForUi,
                        errorCause = exception
                    )
                }
            }
        }
    }

    private fun rootCause(t: Throwable): Throwable {
        var cur = t
        // Ограничим глубину на всякий случай
        repeat(12) {
            val next = cur.cause ?: return cur
            if (next === cur) return cur
            cur = next
        }
        return cur
    }

    private fun chooseBestMessage(rootMessage: String, outerMessage: String): String {
        val root = rootMessage.trim()
        val outer = outerMessage.trim()

        val rootLooksUseful = root.contains("cleartest", ignoreCase = true) ||
                root.contains("cleartext", ignoreCase = true) ||
                root.contains("ssl", ignoreCase = true) ||
                root.contains("cert", ignoreCase = true) ||
                root.contains("trust", ignoreCase = true) ||
                root.contains("handshake", ignoreCase = true) ||
                root.contains("unknownhost", ignoreCase = true) ||
                root.contains("refused", ignoreCase = true) ||
                root.contains("timeout", ignoreCase = true)

        return when {
            rootLooksUseful -> root
            outer.isNotBlank() -> outer
            else -> root
        }
    }

    private fun extractStatusCode(message: String): Int? {
        val regex = Regex("\\b(\\d{3})\\b")
        val code = regex.find(message)?.value?.toIntOrNull()
        return if (code != null && code in 100..599) code else null
    }

    private fun shorten(s: String, max: Int = 180): String {
        val t = s.trim()
        return if (t.length <= max) t else t.take(max) + "…"
    }
}
