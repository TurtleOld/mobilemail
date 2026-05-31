package com.mobilemail.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.mobilemail.data.preferences.PreferencesManager
import com.mobilemail.util.LogRedactor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MobileMailFirebaseMessagingService : FirebaseMessagingService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        val topic = message.data["topic"]?.trim().orEmpty()
        if (topic.isEmpty()) {
            return
        }

        serviceScope.launch {
            try {
                val client = NtfyClient(applicationContext)
                MobileMailPushOrchestrator
                    .resolvePendingPayloads(topic, client.fetchPendingMessages(topic))
                    .forEach { resolved ->
                    PushNotificationPublisher.show(
                        context = applicationContext,
                        payload = resolved.payload,
                        fallbackTitle = resolved.fallbackTitle,
                        fallbackBody = resolved.payload.subject
                            ?: applicationContext.getString(com.mobilemail.R.string.notification_new_message_body)
                    )
                }
            } catch (error: Exception) {
                Log.e("MobileMailFCM", "Failed to fetch ntfy payload", error)
            }
        }
    }

    override fun onNewToken(token: String) {
        Log.d("MobileMailFCM", "FCM token refreshed: ${LogRedactor.redact("token=$token")}")
        serviceScope.launch {
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

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
