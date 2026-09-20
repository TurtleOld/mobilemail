package com.mobilemail.data.update

import android.content.Context

/**
 * Держит единственный [UpdateApkCleaner] на процесс, чтобы координаторы
 * загрузки и установки и фоновая очистка делили одну блокировку использования
 * APK.
 */
object UpdateApkCleanerHolder {
    @Volatile
    private var instance: UpdateApkCleaner? = null

    fun get(context: Context): UpdateApkCleaner {
        val existing = instance
        if (existing != null) return existing

        synchronized(this) {
            val alreadyCreated = instance
            if (alreadyCreated != null) return alreadyCreated

            val applicationContext = context.applicationContext
            val cleaner = UpdateApkCleaner(
                downloadStore = UpdateDownloadStore(applicationContext),
                installStore = UpdateInstallStore(applicationContext)
            )
            instance = cleaner
            return cleaner
        }
    }
}
