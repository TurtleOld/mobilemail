package com.mobilemail.ui.common

import com.mobilemail.data.jmap.TwoFactorRequiredException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object ErrorMapper {
    fun mapException(exception: Throwable): AppError {
        return when (exception) {
            is TwoFactorRequiredException -> AppError.TwoFactorRequired(
                errorMessage = exception.message ?: "Требуется двухфакторная авторизация",
                errorCause = exception
            )
            is SocketTimeoutException -> AppError.NetworkError(
                errorMessage = exception.message ?: "Timeout",
                errorCause = exception,
                isTimeout = true
            )
            is ConnectException, is UnknownHostException -> AppError.NetworkError(
                errorMessage = exception.message ?: "Connection error",
                errorCause = exception,
                isConnectionError = true
            )
            else -> {
                val message = exception.message ?: exception.javaClass.simpleName
                when {
                    message.contains("401", ignoreCase = true) ||
                    message.contains("403", ignoreCase = true) -> AppError.AuthError(message, exception)
                    message.contains("404", ignoreCase = true) -> AppError.ServerError(message, exception, 404)
                    message.contains("500", ignoreCase = true) ||
                    message.contains("502", ignoreCase = true) ||
                    message.contains("503", ignoreCase = true) -> {
                        val statusCode = extractStatusCode(message)
                        AppError.ServerError(message, exception, statusCode)
                    }
                    message.contains("parse", ignoreCase = true) ||
                    message.contains("json", ignoreCase = true) -> AppError.ParseError(message, exception)
                    message.contains("timeout", ignoreCase = true) -> AppError.NetworkError(
                        errorMessage = message,
                        errorCause = exception,
                        isTimeout = true
                    )
                    message.contains("connect", ignoreCase = true) -> AppError.NetworkError(
                        errorMessage = message,
                        errorCause = exception,
                        isConnectionError = true
                    )
                    else -> AppError.UnknownError(message, exception)
                }
            }
        }
    }
    
    private fun extractStatusCode(message: String): Int? {
        val regex = Regex("(\\d{3})")
        return regex.find(message)?.value?.toIntOrNull()
    }
}
