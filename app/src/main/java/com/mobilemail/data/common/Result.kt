@file:Suppress("NOTHING_TO_INLINE")
package com.mobilemail.data.common

typealias Result<T> = com.mobilemail.domain.common.Result<T>
typealias ResultSuccess<T> = com.mobilemail.domain.common.Result.Success<T>
typealias ResultError = com.mobilemail.domain.common.Result.Error

inline fun <T> runCatching(noinline block: () -> T): com.mobilemail.domain.common.Result<T> =
    com.mobilemail.domain.common.runCatching(block)

suspend inline fun <T> runCatchingSuspend(crossinline block: suspend () -> T): com.mobilemail.domain.common.Result<T> =
    com.mobilemail.domain.common.runCatchingSuspend(block)
