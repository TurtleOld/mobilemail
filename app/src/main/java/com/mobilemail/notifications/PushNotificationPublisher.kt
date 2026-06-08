package com.mobilemail.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mobilemail.MainActivity
import com.mobilemail.R
import com.mobilemail.data.preferences.NotificationPrivacyMode

object PushNotificationPublisher {
    private const val CHANNEL_ID = "mail_messages"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_mail),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_mail_description)
        }
        manager.createNotificationChannel(channel)
    }

    fun show(
        context: Context,
        payload: PushPayload,
        fallbackTitle: String?,
        fallbackBody: String?,
        privacyMode: NotificationPrivacyMode = NotificationPrivacyMode.SHOW_DETAILS,
    ) {
        if (privacyMode == NotificationPrivacyMode.DISABLED) return

        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val title = contentTitle(context, payload, fallbackTitle, privacyMode)
        val body = contentBody(context, payload, fallbackBody, privacyMode)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(PushNotificationParser.EXTRA_MESSAGE_ID, payload.target.messageId)
            payload.target.accountId?.let { putExtra(PushNotificationParser.EXTRA_ACCOUNT_ID, it) }
            payload.target.server?.let { putExtra(PushNotificationParser.EXTRA_SERVER, it) }
            payload.target.email?.let { putExtra(PushNotificationParser.EXTRA_EMAIL, it) }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            payload.target.messageId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(visibilityFor(privacyMode))
            .setPublicVersion(publicVersion(context))
            .build()

        NotificationManagerCompat.from(context).notify(payload.target.messageId.hashCode(), notification)
    }

    private fun contentTitle(
        context: Context,
        payload: PushPayload,
        fallbackTitle: String?,
        privacyMode: NotificationPrivacyMode
    ): String {
        if (privacyMode == NotificationPrivacyMode.PRIVATE_CONTENT) {
            return context.getString(R.string.notification_new_message_title)
        }

        return payload.fromName
            ?: fallbackTitle
            ?: context.getString(R.string.notification_new_message_title)
    }

    private fun contentBody(
        context: Context,
        payload: PushPayload,
        fallbackBody: String?,
        privacyMode: NotificationPrivacyMode
    ): String {
        if (privacyMode == NotificationPrivacyMode.PRIVATE_CONTENT) {
            return context.getString(R.string.notification_new_message_body)
        }

        return payload.subject
            ?: fallbackBody
            ?: context.getString(R.string.notification_new_message_body)
    }

    private fun visibilityFor(privacyMode: NotificationPrivacyMode): Int {
        return if (privacyMode == NotificationPrivacyMode.HIDE_ON_LOCK_SCREEN) {
            NotificationCompat.VISIBILITY_SECRET
        } else {
            NotificationCompat.VISIBILITY_PRIVATE
        }
    }

    private fun publicVersion(context: Context): android.app.Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.notification_new_message_title))
            .setContentText(context.getString(R.string.notification_new_message_body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }
}
