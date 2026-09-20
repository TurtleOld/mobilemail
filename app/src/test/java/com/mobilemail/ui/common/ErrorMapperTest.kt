package com.mobilemail.ui.common

import com.mobilemail.data.jmap.OAuthTokenExpiredException
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ErrorMapperTest {

    @Test
    fun `mapException maps unknown host to network connection error`() {
        val error = ErrorMapper.mapException(UnknownHostException("dns failed"))
        assertTrue(error is AppError.NetworkError)
        val networkError = error as AppError.NetworkError
        assertTrue(networkError.isConnectionError)
    }

    @Test
    fun `mapException maps a terminal token expiry directly to AuthError`() {
        // Причина сбоя уже классифицирована в OAuthRefreshFailureClassifier —
        // ErrorMapper не должен заново угадывать её по тексту сообщения.
        val cause = SocketTimeoutException("timeout")
        val error = ErrorMapper.mapException(
            OAuthTokenExpiredException("Не удалось обновить токен: invalid_grant", cause)
        )
        assertTrue(error is AppError.AuthError)
    }
}
