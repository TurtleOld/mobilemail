package com.mobilemail.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.retryWhen

fun <T> Flow<T>.retryWithDelay(
    retries: Int = 3,
    initialDelay: Long = 1000,
    maxDelay: Long = 10000,
    factor: Double = 2.0,
    shouldRetry: (Throwable) -> Boolean = { true }
): Flow<T> {
    var currentDelay = initialDelay
    return retryWhen { cause, attempt ->
        if (attempt < retries && shouldRetry(cause)) {
            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
            true
        } else {
            false
        }
    }
}

fun <T> Flow<T>.retryExponential(
    retries: Int = 3,
    initialDelay: Long = 1000
): Flow<T> = retryWithDelay(
    retries = retries,
    initialDelay = initialDelay,
    factor = 2.0
)

fun <T> Flow<T>.timeoutOrDefault(
    timeoutMillis: Long,
    defaultValue: T
): Flow<T> = catch {
    if (timeoutMillis > 0L) {
        emit(defaultValue)
    }
}

fun <T> Flow<T>.onErrorReturn(defaultValue: T): Flow<T> = catch { emit(defaultValue) }
