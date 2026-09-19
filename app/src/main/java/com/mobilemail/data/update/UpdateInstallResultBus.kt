package com.mobilemail.data.update

import com.mobilemail.domain.port.InstallCallback
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Процессный мост между [UpdateInstallReceiver] и координатором установки:
 * receiver лишь публикует результат, не запуская никаких Activity.
 */
object UpdateInstallResultBus {
    private val mutableCallbacks = MutableSharedFlow<InstallCallback>(extraBufferCapacity = 8)
    val callbacks: SharedFlow<InstallCallback> = mutableCallbacks.asSharedFlow()

    fun publish(callback: InstallCallback) {
        mutableCallbacks.tryEmit(callback)
    }
}
