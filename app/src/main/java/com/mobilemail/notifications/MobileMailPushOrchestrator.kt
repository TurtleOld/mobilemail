package com.mobilemail.notifications

internal data class ResolvedPushNotification(
    val payload: PushPayload,
    val fallbackTitle: String?
)

internal object MobileMailPushOrchestrator {
    fun resolvePendingPayloads(
        topic: String,
        envelopes: List<NtfyEnvelope>
    ): List<ResolvedPushNotification> {
        val fallbackAccountId = NtfyTopics.accountIdFromTopic(topic)
        return envelopes.mapNotNull { envelope ->
            val payload = PushNotificationParser.fromPayloadJson(envelope.message, fallbackAccountId)
                ?: return@mapNotNull null
            ResolvedPushNotification(
                payload = payload,
                fallbackTitle = envelope.title
            )
        }
    }

    fun accountIdsForResubscribe(rawAccountIds: List<String>): List<String> {
        return rawAccountIds.map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }
}
