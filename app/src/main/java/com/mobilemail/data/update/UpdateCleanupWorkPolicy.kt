package com.mobilemail.data.update

import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import java.util.concurrent.TimeUnit

/**
 * Политика фоновой очистки просроченного APK. Запрос не ограничен сетью:
 * удаление локального файла не зависит от подключения.
 */
object UpdateCleanupWorkPolicy {
    const val UNIQUE_WORK = "update-apk-cleanup"

    fun buildWorkRequest(initialDelayMillis: Long): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<UpdateCleanupWorker>()
            .setInitialDelay(initialDelayMillis.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .build()
}
