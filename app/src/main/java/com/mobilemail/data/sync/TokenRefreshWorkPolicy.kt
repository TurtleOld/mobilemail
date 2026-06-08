package com.mobilemail.data.sync

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import java.util.concurrent.TimeUnit

object TokenRefreshWorkPolicy {
    const val UNIQUE_PERIODIC = "token-refresh-periodic"
    const val TAG_PERIODIC = "token-refresh-periodic"

    const val BACKOFF_DELAY_SECONDS = 30L
    const val PERIODIC_INTERVAL_HOURS = 6L
    const val REFRESH_WINDOW_HOURS = 24L

    fun connectedConstraints(): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }

    fun buildPeriodicWorkRequest(): PeriodicWorkRequest {
        return PeriodicWorkRequestBuilder<TokenRefreshWorker>(PERIODIC_INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(connectedConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_SECONDS, TimeUnit.SECONDS)
            .addTag(TAG_PERIODIC)
            .build()
    }

    fun periodicExistingWorkPolicy(): ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP
}
