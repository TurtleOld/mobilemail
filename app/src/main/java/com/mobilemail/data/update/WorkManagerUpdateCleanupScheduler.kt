package com.mobilemail.data.update

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.mobilemail.domain.port.UpdateCleanupSchedulerPort

/**
 * Планирует очистку APK через WorkManager без сетевого ограничения: система
 * может задержать выполнение, поэтому срок дополнительно проверяется при
 * запуске и перед использованием APK.
 */
class WorkManagerUpdateCleanupScheduler(
    private val context: Context,
    private val now: () -> Long = System::currentTimeMillis
) : UpdateCleanupSchedulerPort {

    override fun schedule(completedAtMillis: Long) {
        val delay = updateCleanupDelayMillis(completedAtMillis, now())
        WorkManager.getInstance(context).enqueueUniqueWork(
            UpdateCleanupWorkPolicy.UNIQUE_WORK,
            ExistingWorkPolicy.REPLACE,
            UpdateCleanupWorkPolicy.buildWorkRequest(delay)
        )
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(UpdateCleanupWorkPolicy.UNIQUE_WORK)
    }
}
