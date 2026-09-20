package com.mobilemail.data.update

import com.mobilemail.domain.port.UpdateApkCleanupPort
import com.mobilemail.domain.port.UpdateDownloadPersistencePort
import com.mobilemail.domain.port.UpdateInstallPersistencePort
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Единая проверка пригодности и очистка скачанного APK: срок хранения,
 * удаление только собственного файла и связанных записей.
 *
 * Блокировка [usage] сериализует очистку с использованием APK, поэтому файл не
 * удаляется, пока его копирует системный установщик. Один экземпляр на процесс
 * делится координаторами и фоновой задачей через [UpdateApkCleanerHolder].
 */
class UpdateApkCleaner(
    private val downloadStore: UpdateDownloadPersistencePort,
    private val installStore: UpdateInstallPersistencePort,
    private val now: () -> Long = System::currentTimeMillis,
    private val deleteFile: (String) -> Boolean = { File(it).delete() }
) : UpdateApkCleanupPort {

    private val usage = Mutex()

    override suspend fun isExpired(): Boolean =
        isApkExpired(downloadStore.getCompletedAtMillis(), now())

    override suspend fun <T> duringInstallTransfer(block: suspend () -> T): T =
        usage.withLock { block() }

    override suspend fun clean(apkFilePath: String) {
        usage.withLock {
            runCatching { deleteFile(apkFilePath) }
            installStore.clear()
            if (!File(apkFilePath).exists()) {
                downloadStore.clear()
            }
        }
    }

    /**
     * Удаляет APK и связанные записи, если срок истёк или установленный код уже
     * не ниже скачанного. Возвращает `true`, если очистка действительно
     * выполнена, и не падает, если файл уже удалён системой.
     */
    suspend fun cleanUpIfNeeded(installedVersionCode: Int): Boolean {
        val pending = downloadStore.loadPendingDownload() ?: return false
        val superseded = installedVersionCode >= pending.manifest.versionCode
        if (!isExpired() && !superseded) return false
        clean(pending.apkFilePath)
        return true
    }
}
