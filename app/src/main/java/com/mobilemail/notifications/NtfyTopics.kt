package com.mobilemail.notifications

import com.google.firebase.messaging.FirebaseMessaging
import com.mobilemail.BuildConfig

object NtfyTopics {
    private val topicPattern = BuildConfig.NTFY_TOPIC_PATTERN.ifBlank { "homemail-user-{accountId}" }
    private val supportedMarkers = listOf("{accountId}", "{user}")

    fun forAccount(accountId: String): String {
        val marker = topicMarker() ?: return topicPattern
        return topicPattern.replace(marker, accountId)
    }

    fun accountIdFromTopic(topic: String): String? {
        val marker = topicMarker() ?: return null
        val markerIndex = topicPattern.indexOf(marker)
        if (markerIndex < 0) return null

        val prefix = topicPattern.substring(0, markerIndex)
        val suffix = topicPattern.substring(markerIndex + marker.length)
        if (!topic.startsWith(prefix) || !topic.endsWith(suffix)) {
            return null
        }

        return topic.removePrefix(prefix).removeSuffix(suffix).takeIf { it.isNotBlank() }
    }

    fun subscribe(accountId: String) {
        FirebaseMessaging.getInstance().subscribeToTopic(forAccount(accountId))
    }

    fun unsubscribe(accountId: String) {
        FirebaseMessaging.getInstance().unsubscribeFromTopic(forAccount(accountId))
    }

    private fun topicMarker(): String? {
        return supportedMarkers.firstOrNull { topicPattern.contains(it) }
    }
}
