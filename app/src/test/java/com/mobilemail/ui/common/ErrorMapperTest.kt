package com.mobilemail.ui.common

import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.UnknownHostException

class ErrorMapperTest {

    @Test
    fun `mapException maps unknown host to network connection error`() {
        val error = ErrorMapper.mapException(UnknownHostException("dns failed"))
        assertTrue(error is AppError.NetworkError)
        val networkError = error as AppError.NetworkError
        assertTrue(networkError.isConnectionError)
    }
}
