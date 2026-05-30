package com.mobilemail.data.sync

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import java.util.concurrent.TimeUnit

object OfflineQueueWorkPolicy {
    const val UNIQUE_ONE_TIME = "offline-queue-once"
    const val UNIQUE_PERIODIC = "offline-queue-periodic"
    const val TAG_ONE_TIME = "offline-queue-one-time"
    const val TAG_PERIODIC = "offline-queue-periodic"

    const val BACKOFF_DELAY_SECONDS = 30L
    const val PERIODIC_INTERVAL_MINUTES = 15L

    fun connectedConstraints(): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }

    fun buildOneTimeWorkRequest(): OneTimeWorkRequest {
        return OneTimeWorkRequestBuilder<OfflineQueueWorker>()
            .setConstraints(connectedConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_SECONDS, TimeUnit.SECONDS)
            .addTag(TAG_ONE_TIME)
            .build()
    }

    fun buildPeriodicWorkRequest(): PeriodicWorkRequest {
        return PeriodicWorkRequestBuilder<OfflineQueueWorker>(PERIODIC_INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(connectedConstraints())
            .addTag(TAG_PERIODIC)
            .build()
    }

    fun oneTimeExistingWorkPolicy(): ExistingWorkPolicy = ExistingWorkPolicy.KEEP

    fun periodicExistingWorkPolicy(): ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP
}
