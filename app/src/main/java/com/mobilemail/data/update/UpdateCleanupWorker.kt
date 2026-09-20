package com.mobilemail.data.update

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mobilemail.BuildConfig

/**
 * Фоновая очистка просроченного APK при ближайшей возможности, которую
 * предоставит система. Решение принимает [UpdateApkCleaner]: удаление
 * выполняется только для собственного файла обновления и связанных записей.
 */
class UpdateCleanupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        runCatching {
            UpdateApkCleanerHolder.get(applicationContext).cleanUpIfNeeded(BuildConfig.VERSION_CODE)
        }
        return Result.success()
    }
}
