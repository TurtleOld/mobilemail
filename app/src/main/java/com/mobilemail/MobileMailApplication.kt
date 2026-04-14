package com.mobilemail

import android.app.Application
import com.mobilemail.data.sync.OfflineQueueWorker
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel

class MobileMailApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        OfflineQueueWorker.schedulePeriodic(this)
        OneSignal.Debug.logLevel = LogLevel.NONE
        OneSignal.initWithContext(this, BuildConfig.ONESIGNAL_APP_ID)
    }
}
