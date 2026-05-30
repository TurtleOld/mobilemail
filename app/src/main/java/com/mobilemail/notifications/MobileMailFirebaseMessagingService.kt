package com.mobilemail.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.mobilemail.data.preferences.PreferencesManager
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
                val fallbackAccountId = NtfyTopics.accountIdFromTopic(topic)
                client.fetchPendingMessages(topic).forEach { envelope ->
                    val payload = PushNotificationParser.fromPayloadJson(envelope.message, fallbackAccountId) ?: return@forEach
                    PushNotificationPublisher.show(
                        context = applicationContext,
                        payload = payload,
                        fallbackTitle = envelope.title,
                        fallbackBody = payload.subject ?: applicationContext.getString(com.mobilemail.R.string.notification_new_message_body)
                    )
                }
            } catch (error: Exception) {
                Log.e("MobileMailFCM", "Failed to fetch ntfy payload", error)
            }
        }
    }

    override fun onNewToken(token: String) {
        Log.d("MobileMailFCM", "FCM token refreshed: ${token.take(12)}")
        serviceScope.launch {
            runCatching {
                val preferencesManager = PreferencesManager(applicationContext)
                preferencesManager.getSavedAccounts()
                    .map { it.accountId }
                    .distinct()
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
