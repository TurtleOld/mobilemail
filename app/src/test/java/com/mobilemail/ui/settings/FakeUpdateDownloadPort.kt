package com.mobilemail.ui.settings

import com.mobilemail.domain.model.UpdateReleaseManifest
import com.mobilemail.domain.port.DownloadStatus
import com.mobilemail.domain.port.UpdateDownloadPort
import java.util.concurrent.atomic.AtomicLong

/**
 * Управляемая тестами реализация [UpdateDownloadPort]: статус каждого
 * download ID выставляется вручную через [setStatus], enqueue считается и
 * возвращает нарастающие ID, cancel фиксируется в [cancelledIds].
 */
class FakeUpdateDownloadPort : UpdateDownloadPort {
    private val nextId = AtomicLong(1)
    private val statuses = mutableMapOf<Long, DownloadStatus>()

    val enqueuedManifests = mutableListOf<UpdateReleaseManifest>()
    val cancelledIds = mutableListOf<Long>()
    val downloadIdsByDestination = mutableMapOf<String, Long>()

    override fun enqueue(manifest: UpdateReleaseManifest): Long {
        val id = nextId.getAndIncrement()
        enqueuedManifests.add(manifest)
        statuses[id] = DownloadStatus.Pending
        return id
    }

    override fun pollStatus(downloadId: Long): DownloadStatus =
        statuses[downloadId] ?: DownloadStatus.NotFound

    override fun findDownloadIdByDestination(apkFilePath: String): Long? =
        downloadIdsByDestination[apkFilePath]

    override fun cancel(downloadId: Long) {
        cancelledIds.add(downloadId)
        statuses.remove(downloadId)
    }

    fun setStatus(downloadId: Long, status: DownloadStatus) {
        statuses[downloadId] = status
    }
}
