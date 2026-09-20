package com.mobilemail.domain.port

import com.mobilemail.domain.model.UpdateReleaseManifest

/**
 * Установочная попытка, которая должна пережить завершение процесса: какой
 * релиз и какой файл передаются системному установщику. Сам результат попытки
 * при следующем запуске согласуется с фактически установленной версией.
 */
data class PendingInstall(
    val manifest: UpdateReleaseManifest,
    val apkFilePath: String
)

/**
 * Хранилище активной установочной сессии, переживающее перезапуск процесса.
 * Позволяет при следующем запуске согласовать сохранённую попытку с фактически
 * установленной версией, не открывая системный установщик повторно.
 */
interface UpdateInstallPersistencePort {
    suspend fun savePendingInstall(pending: PendingInstall)
    suspend fun loadPendingInstall(): PendingInstall?
    suspend fun clear()
}
