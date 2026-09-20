package com.mobilemail.ui.settings

import com.mobilemail.domain.port.PendingDownload
import com.mobilemail.domain.port.UpdateDownloadPersistencePort

class FakeUpdateDownloadPersistencePort(
    private var pending: PendingDownload? = null,
    private var completedAtMillis: Long? = null
) : UpdateDownloadPersistencePort {
    val clearCallCount get() = clearCalls

    private var clearCalls = 0

    override suspend fun savePendingDownload(pending: PendingDownload) {
        this.pending = pending
    }

    override suspend fun loadPendingDownload(): PendingDownload? = pending

    override suspend fun saveDownloadId(downloadId: Long) {
        pending = pending?.copy(downloadId = downloadId)
    }

    override suspend fun markCompletedNow(completedAtMillis: Long) {
        if (this.completedAtMillis == null) {
            this.completedAtMillis = completedAtMillis
        }
    }

    override suspend fun getCompletedAtMillis(): Long? = completedAtMillis

    override suspend fun clear() {
        pending = null
        clearCalls++
    }
}
