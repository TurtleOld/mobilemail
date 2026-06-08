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

object MailSyncWorkPolicy {
    const val UNIQUE_ONE_TIME = "mail-sync-once"
    const val UNIQUE_PERIODIC = "mail-sync-periodic"
    const val TAG_ONE_TIME = "mail-sync-one-time"
    const val TAG_PERIODIC = "mail-sync-periodic"

    const val BACKOFF_DELAY_SECONDS = 30L
    const val PERIODIC_INTERVAL_MINUTES = 15L
    const val PAGE_SIZE = 50

    fun connectedConstraints(): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }

    fun buildOneTimeWorkRequest(): OneTimeWorkRequest {
        return OneTimeWorkRequestBuilder<MailSyncWorker>()
            .setConstraints(connectedConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_SECONDS, TimeUnit.SECONDS)
            .addTag(TAG_ONE_TIME)
            .build()
    }

    fun buildPeriodicWorkRequest(): PeriodicWorkRequest {
        return PeriodicWorkRequestBuilder<MailSyncWorker>(PERIODIC_INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(connectedConstraints())
            .addTag(TAG_PERIODIC)
            .build()
    }

    fun oneTimeExistingWorkPolicy(): ExistingWorkPolicy = ExistingWorkPolicy.KEEP

    fun periodicExistingWorkPolicy(): ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP
}
