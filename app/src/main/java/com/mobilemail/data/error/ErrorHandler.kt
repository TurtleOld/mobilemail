package com.mobilemail.data.error

import android.util.Log
import com.mobilemail.ui.common.AppError
import com.mobilemail.ui.common.ErrorMapper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.retryWhen

object ErrorHandler {
    private const val TAG = "ErrorHandler"
    
    fun logError(error: AppError, tag: String = TAG) {
        when (error) {
            is AppError.NetworkError -> Log.w(tag, "Network error: ${error.message}", error.cause)
            is AppError.AuthError -> Log.w(tag, "Auth error: ${error.message}", error.cause)
            is AppError.TwoFactorRequired -> Log.w(tag, "Two-factor authentication required: ${error.message}", error.cause)
            is AppError.ParseError -> Log.e(tag, "Parse error: ${error.message}", error.cause)
            is AppError.ServerError -> Log.e(tag, "Server error: ${error.message}", error.cause)
            is AppError.UnknownError -> Log.e(tag, "Unknown error: ${error.message}", error.cause)
        }
    }
    
    fun handleError(throwable: Throwable): AppError {
        return ErrorMapper.mapException(throwable).also { error ->
            logError(error)
        }
    }
    
    suspend fun <T> retryWithBackoff(
        retries: Int = 3,
        initialDelay: Long = 1000,
        maxDelay: Long = 10000,
        factor: Double = 2.0,
        shouldRetry: (Throwable) -> Boolean = { throwable ->
            val error = handleError(throwable)
            error !is AppError.AuthError && error !is AppError.TwoFactorRequired
        },
        block: suspend () -> T
    ): Result<T> {
        var currentDelay = initialDelay
        var lastException: Throwable? = null
        
        repeat(retries) { attempt ->
            try {
                return Result.success(block())
            } catch (e: Throwable) {
                lastException = e
                val error = handleError(e)
                
                if (!shouldRetry(e) || attempt == retries - 1) {
                    return Result.failure(e)
                }
                
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
            }
        }
        
        return Result.failure(lastException ?: Exception("Unknown error"))
    }
    
    fun <T> Flow<T>.retryWithErrorHandling(
        retries: Int = 3,
        initialDelay: Long = 1000
    ): Flow<T> {
        var currentDelay = initialDelay
        return retryWhen { cause, attempt ->
            if (attempt < retries) {
                val error = handleError(cause)
                if (error !is AppError.AuthError && error !is AppError.TwoFactorRequired) {
                    delay(currentDelay)
                    currentDelay *= 2
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }
    }
}
