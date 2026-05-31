package com.mobilemail.domain.usecase

import android.content.Intent
import com.mobilemail.notifications.PushNavigationStore
import com.mobilemail.notifications.PushNotificationParser

class PublishPushNavigationFromIntentUseCase {
    operator fun invoke(intent: Intent?) {
        PushNavigationStore.publish(PushNotificationParser.fromIntent(intent))
    }
}
