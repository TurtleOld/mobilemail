package com.mobilemail.data.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Принимает системный сигнал о завершении загрузки и передаёт download ID в
 * [UpdateDownloadSignalBus]. Никаких Activity из receiver не запускается;
 * окончательное решение принимает координатор после сверки с DownloadManager.
 */
class UpdateDownloadReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, INVALID_DOWNLOAD_ID)
        if (downloadId == INVALID_DOWNLOAD_ID) return
        UpdateDownloadSignalBus.publish(downloadId)
    }

    private companion object {
        const val INVALID_DOWNLOAD_ID = -1L
    }
}
