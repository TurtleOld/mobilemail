package com.mobilemail.data.common

sealed class Result<out T> {
    data class Success<out T>(val data: T) : Result<T>()
    data class Error(val exception: Throwable) : Result<Nothing>()
    
    val isSuccess: Boolean
        get() = this is Success
    
    val isError: Boolean
        get() = this is Error
    
    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Error -> null
    }
    
    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw exception
    }
    
    inline fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
    }
    
    inline fun <R> flatMap(transform: (T) -> Result<R>): Result<R> = when (this) {
        is Success -> transform(data)
        is Error -> this
    }
    
    inline fun onSuccess(action: (T) -> Unit): Result<T> {
        if (this is Success) action(data)
        return this
    }
    
    inline fun onError(action: (Throwable) -> Unit): Result<T> {
        if (this is Error) action(exception)
        return this
    }
}

inline fun <T> Result<T>.getOrElse(defaultValue: () -> T): T = when (this) {
    is Result.Success -> data
    is Result.Error -> defaultValue()
}

inline fun <T, R> Result<T>.fold(
    onError: (Throwable) -> R,
    onSuccess: (T) -> R
): R = when (this) {
    is Result.Success -> onSuccess(data)
    is Result.Error -> onError(exception)
}

inline fun <T> runCatching(block: () -> T): Result<T> = try {
    Result.Success(block())
} catch (e: Throwable) {
    Result.Error(e)
}

suspend inline fun <T> runCatchingSuspend(crossinline block: suspend () -> T): Result<T> = try {
    Result.Success(block())
} catch (e: Throwable) {
    Result.Error(e)
}
