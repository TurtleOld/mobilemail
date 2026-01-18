package com.mobilemail.ui.common

sealed class AppError(
    val message: String,
    val cause: Throwable? = null
) {
    data class NetworkError(
        val errorMessage: String,
        val errorCause: Throwable? = null,
        val isTimeout: Boolean = false,
        val isConnectionError: Boolean = false
    ) : AppError(errorMessage, errorCause)
    
    data class AuthError(
        val errorMessage: String,
        val errorCause: Throwable? = null
    ) : AppError(errorMessage, errorCause)
    
    data class TwoFactorRequired(
        val errorMessage: String = "Требуется двухфакторная авторизация",
        val errorCause: Throwable? = null
    ) : AppError(errorMessage, errorCause)
    
    data class ParseError(
        val errorMessage: String,
        val errorCause: Throwable? = null
    ) : AppError(errorMessage, errorCause)
    
    data class ServerError(
        val errorMessage: String,
        val errorCause: Throwable? = null,
        val statusCode: Int? = null
    ) : AppError(errorMessage, errorCause)
    
    data class UnknownError(
        val errorMessage: String,
        val errorCause: Throwable? = null
    ) : AppError(errorMessage, errorCause)
    
    fun getUserMessage(): String = when (this) {
        is NetworkError -> when {
            isTimeout -> "Превышено время ожидания. Проверьте подключение к сети."
            isConnectionError -> "Не удалось подключиться к серверу. Проверьте подключение к интернету."
            else -> message.ifEmpty { "Ошибка сети" }
        }
        is AuthError -> message.ifEmpty { "Ошибка авторизации. Проверьте логин и пароль." }
        is TwoFactorRequired -> message.ifEmpty { "Требуется код двухфакторной авторизации." }
        is ParseError -> "Ошибка обработки данных. Попробуйте позже."
        is ServerError -> when (statusCode) {
            404 -> "Сервер не найден. Проверьте адрес сервера."
            401, 403 -> "Неверные учетные данные."
            500, 502, 503 -> "Сервер временно недоступен. Попробуйте позже."
            else -> message.ifEmpty { "Ошибка сервера" }
        }
        is UnknownError -> message.ifEmpty { "Произошла неизвестная ошибка" }
    }
}
