package com.mobilemail.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.mobilemail.data.preferences.PreferencesManager
import com.mobilemail.data.sync.MailSyncWorker
import com.mobilemail.util.LogRedactor
import kotlinx.coroutines.runBlocking

class MobileMailFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val topic = message.data["topic"]?.trim().orEmpty()
        if (topic.isEmpty()) {
            return
        }

        MailSyncWorker.scheduleNow(applicationContext)
        // The service is destroyed as soon as onMessageReceived returns, which
        // cancels any coroutine launched in a service-scoped scope. Fetching the
        // payload and showing the notification must outlive the service, so it
        // runs in WorkManager.
        NtfyPushWorker.scheduleNow(applicationContext, topic)
    }

    override fun onNewToken(token: String) {
        Log.d("MobileMailFCM", "FCM token refreshed: ${LogRedactor.redact("token=$token")}")
        runBlocking {
            runCatching {
                val preferencesManager = PreferencesManager(applicationContext)
                val accountIds = preferencesManager.getSavedAccounts()
                    .map { it.accountId }
                MobileMailPushOrchestrator
                    .accountIdsForResubscribe(accountIds)
                    .forEach { accountId -> NtfyTopics.subscribe(accountId) }
            }.onFailure { error ->
                Log.e("MobileMailFCM", "Failed to re-subscribe topics on token refresh", error)
            }
        }
    }
}
