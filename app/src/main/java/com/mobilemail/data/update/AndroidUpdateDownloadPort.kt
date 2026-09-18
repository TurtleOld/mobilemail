package com.mobilemail.data.update

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import androidx.core.database.getLongOrNull
import com.mobilemail.domain.model.UpdateReleaseManifest
import com.mobilemail.domain.port.DownloadStatus
import com.mobilemail.domain.port.UpdateDownloadPort
import java.io.File

/**
 * Ставит APK в очередь системного DownloadManager, разрешая Wi-Fi и мобильную
 * сеть (включая metered), и размещает файл в app-specific внешнем хранилище —
 * доступ к нему не требует разрешения на общий доступ к файлам.
 *
 * Системное уведомление о прогрессе оставлено штатным ([DownloadManager.Request.setNotificationVisibility]
 * со значением по умолчанию для видимого уведомления): его сокрытие потребовало
 * бы разрешения, которое этот тикет не запрашивает.
 */
class AndroidUpdateDownloadPort(private val context: Context) : UpdateDownloadPort {
    private val downloadManager: DownloadManager
        get() = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    override fun enqueue(manifest: UpdateReleaseManifest): Long {
        val fileName = updateApkFileName(manifest)
        val request = DownloadManager.Request(Uri.parse(manifest.apkDownloadUrl))
            .setTitle("Обновление MobileMail")
            .setDescription("Загрузка версии ${manifest.versionName}")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
        return downloadManager.enqueue(request)
    }

    override fun pollStatus(downloadId: Long): DownloadStatus {
        val query = DownloadManager.Query().setFilterById(downloadId)
        downloadManager.query(query).use { cursor ->
            if (!cursor.moveToFirst()) return DownloadStatus.NotFound
            return cursor.toDownloadStatus()
        }
    }

    override fun cancel(downloadId: Long) {
        downloadManager.remove(downloadId)
    }

    private fun Cursor.toDownloadStatus(): DownloadStatus {
        val statusColumn = getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
        return when (val status = getInt(statusColumn)) {
            DownloadManager.STATUS_PENDING -> DownloadStatus.Pending
            DownloadManager.STATUS_RUNNING -> runningStatus()
            DownloadManager.STATUS_PAUSED -> DownloadStatus.Paused
            DownloadManager.STATUS_SUCCESSFUL -> successfulStatus()
            DownloadManager.STATUS_FAILED -> failedStatus()
            else -> DownloadStatus.Failed("Неизвестный статус загрузки: $status")
        }
    }

    private fun Cursor.runningStatus(): DownloadStatus.Running {
        val downloadedColumn = getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
        val totalColumn = getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
        val downloaded = getLong(downloadedColumn)
        val total = getLongOrNull(totalColumn)?.takeIf { it > 0 }
        return DownloadStatus.Running(downloaded, total)
    }

    private fun Cursor.successfulStatus(): DownloadStatus {
        val uriColumn = getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)
        val localUri = getString(uriColumn) ?: return DownloadStatus.Failed("Скачанный файл не найден")
        val filePath = Uri.parse(localUri).path ?: return DownloadStatus.Failed("Скачанный файл не найден")
        return if (File(filePath).exists()) {
            DownloadStatus.Successful(filePath)
        } else {
            DownloadStatus.Failed("Скачанный файл не найден")
        }
    }

    private fun Cursor.failedStatus(): DownloadStatus {
        val reasonColumn = getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
        val reason = getInt(reasonColumn)
        return DownloadStatus.Failed(describeFailureReason(reason))
    }

    private fun describeFailureReason(reason: Int): String = when (reason) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Недостаточно места на устройстве"
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Хранилище недоступно"
        DownloadManager.ERROR_HTTP_DATA_ERROR,
        DownloadManager.ERROR_TOO_MANY_REDIRECTS,
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "Ошибка сети при загрузке"
        DownloadManager.ERROR_CANNOT_RESUME -> "Не удалось продолжить загрузку"
        DownloadManager.ERROR_FILE_ERROR -> "Ошибка записи файла"
        else -> "Загрузка завершилась с ошибкой"
    }
}
