package com.mobilemail

import android.app.Application
import com.mobilemail.data.sync.OfflineQueueWorker
import com.mobilemail.notifications.PushNavigationStore
import com.mobilemail.notifications.PushNotificationParser
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel
import com.onesignal.notifications.INotificationClickEvent
import com.onesignal.notifications.INotificationClickListener

class MobileMailApplication : Application() {
    private val notificationClickListener = object : INotificationClickListener {
        override fun onClick(event: INotificationClickEvent) {
            val target = PushNotificationParser.fromRawPayload(event.notification.rawPayload)
            PushNavigationStore.publish(target)
        }
    }

    override fun onCreate() {
        super.onCreate()
        OfflineQueueWorker.schedulePeriodic(this)
        OneSignal.Debug.logLevel = LogLevel.NONE
        OneSignal.initWithContext(this, BuildConfig.ONESIGNAL_APP_ID)
        OneSignal.Notifications.addClickListener(notificationClickListener)
    }
}
