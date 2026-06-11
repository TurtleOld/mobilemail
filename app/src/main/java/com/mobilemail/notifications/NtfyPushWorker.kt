package com.mobilemail.notifications

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mobilemail.R
import com.mobilemail.data.preferences.NotificationPrivacyMode
import com.mobilemail.data.preferences.PreferencesManager

class NtfyPushWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val topic = inputData.getString(KEY_TOPIC)?.trim().orEmpty()
        if (topic.isEmpty()) {
            return Result.success()
        }

        return try {
            val preferencesManager = PreferencesManager(applicationContext)
            val privacyMode = preferencesManager.getNotificationPrivacyMode()
            if (privacyMode == NotificationPrivacyMode.DISABLED) {
                return Result.success()
            }
            val client = NtfyClient(applicationContext)
            MobileMailPushOrchestrator
                .resolvePendingPayloads(topic, client.fetchPendingMessages(topic))
                .forEach { resolved ->
                    PushNotificationPublisher.show(
                        context = applicationContext,
                        payload = resolved.payload,
                        fallbackTitle = resolved.fallbackTitle,
                        fallbackBody = resolved.payload.subject
                            ?: applicationContext.getString(R.string.notification_new_message_body),
                        privacyMode = privacyMode,
                    )
                }
            Result.success()
        } catch (error: Exception) {
            Log.e(TAG, "Failed to fetch ntfy payload", error)
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "NtfyPushWorker"
        private const val KEY_TOPIC = "topic"
        private const val MAX_RETRIES = 2

        fun scheduleNow(context: Context, topic: String) {
            val request = OneTimeWorkRequestBuilder<NtfyPushWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(Data.Builder().putString(KEY_TOPIC, topic).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "ntfy-push-$topic",
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request
            )
        }
    }
}
