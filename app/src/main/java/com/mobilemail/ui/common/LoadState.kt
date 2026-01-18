package com.mobilemail.ui.common

sealed class LoadState<out T> {
    object Loading : LoadState<Nothing>()
    data class Success<T>(val data: T) : LoadState<T>()
    data class Error(val error: AppError) : LoadState<Nothing>()
    
    val isLoading: Boolean
        get() = this is Loading
    
    val isSuccess: Boolean
        get() = this is Success
    
    val isError: Boolean
        get() = this is Error
    
    fun getOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }
    
    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw IllegalStateException("Ошибка загрузки: ${error.message}", error.cause)
        is Loading -> throw IllegalStateException("Данные еще загружаются")
    }
}
