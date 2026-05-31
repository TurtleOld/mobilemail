package com.mobilemail.data.common

import com.mobilemail.domain.common.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

fun <T> Result<T>.toExceptionOrNull(): Throwable? = when (this) {
    is Result.Success -> null
    is Result.Error -> exception
}

inline fun <T> Result<T>.mapError(transform: (Throwable) -> Throwable): Result<T> = when (this) {
    is Result.Success -> this
    is Result.Error -> Result.Error(transform(exception))
}

inline fun <T, R> Result<T>.fold(
    onError: (Throwable) -> R,
    onSuccess: (T) -> R
): R = when (this) {
    is Result.Success -> onSuccess(data)
    is Result.Error -> onError(exception)
}

fun <T> Flow<Result<T>>.unwrap(): Flow<T> = this.map { result ->
    result.getOrThrow()
}

fun <T> Flow<T>.asResult(): Flow<Result<T>> = this
    .map<T, Result<T>> { Result.Success(it) }
    .catch { emit(Result.Error(it)) }
