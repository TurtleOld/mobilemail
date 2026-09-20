package com.mobilemail.data.update

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Процессный мост между [UpdateDownloadReceiver] и координатором загрузки.
 * Receiver лишь сообщает download ID; координатор по этому сигналу сверяет
 * операцию с фактическим состоянием DownloadManager и не считает сам broadcast
 * доказательством успеха.
 */
object UpdateDownloadSignalBus {
    private val mutableSignals = MutableSharedFlow<Long>(extraBufferCapacity = 8)
    val signals: SharedFlow<Long> = mutableSignals.asSharedFlow()

    fun publish(downloadId: Long) {
        mutableSignals.tryEmit(downloadId)
    }
}
