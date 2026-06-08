package com.mobilemail

import android.app.Application
import com.mobilemail.data.sync.MailSyncWorker
import com.mobilemail.data.sync.OfflineQueueWorker
import com.mobilemail.data.sync.TokenRefreshWorker
import com.mobilemail.notifications.PushNotificationPublisher

class MobileMailApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        TokenRefreshWorker.schedulePeriodic(this)
        MailSyncWorker.schedulePeriodic(this)
        OfflineQueueWorker.schedulePeriodic(this)
        PushNotificationPublisher.ensureChannel(this)
    }
}
