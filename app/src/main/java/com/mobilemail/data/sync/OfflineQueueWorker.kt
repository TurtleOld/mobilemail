package com.mobilemail.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters

class OfflineQueueWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val summary = OfflineQueueManager.processPending(applicationContext as android.app.Application)
        return resolveResult(summary)
    }

    companion object {
        internal fun resolveResult(summary: OfflineQueueSummary): Result {
            return if (summary.failedCount > 0) Result.retry() else Result.success()
        }

        fun scheduleNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                OfflineQueueWorkPolicy.UNIQUE_ONE_TIME,
                OfflineQueueWorkPolicy.oneTimeExistingWorkPolicy(),
                OfflineQueueWorkPolicy.buildOneTimeWorkRequest()
            )
        }

        fun schedulePeriodic(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                OfflineQueueWorkPolicy.UNIQUE_PERIODIC,
                OfflineQueueWorkPolicy.periodicExistingWorkPolicy(),
                OfflineQueueWorkPolicy.buildPeriodicWorkRequest()
            )
        }
    }
}
