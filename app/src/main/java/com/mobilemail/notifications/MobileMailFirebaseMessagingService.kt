package com.mobilemail.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class MobileMailFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val topic = message.data["topic"]?.trim().orEmpty()
        if (topic.isEmpty()) {
            return
        }

        runBlocking(Dispatchers.IO) {
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
    }
}
