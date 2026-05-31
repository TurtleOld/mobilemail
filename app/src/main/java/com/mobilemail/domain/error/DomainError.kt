package com.mobilemail.domain.error

sealed class DomainError : Exception() {
    data class NotFound(val resource: String) : DomainError() {
        override val message: String = "$resource not found"
    }

    data class NetworkError(override val cause: Throwable) : DomainError() {
        override val message: String = "Network error: ${cause.message}"
    }

    data class AuthError(override val message: String = "Authentication failed") : DomainError()

    data class ServerError(val code: Int, override val message: String) : DomainError()

    data class ValidationError(override val message: String) : DomainError()

    data class Unknown(override val cause: Throwable) : DomainError() {
        override val message: String = cause.message ?: "Unknown error"
    }
}
