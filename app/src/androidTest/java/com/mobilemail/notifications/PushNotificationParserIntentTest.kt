package com.mobilemail.notifications

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PushNotificationParserIntentTest {

    @Test
    fun fromIntent_parsesMessageTargetExtras() {
        val intent = Intent().apply {
            putExtra(PushNotificationParser.EXTRA_MESSAGE_ID, "m-123")
            putExtra(PushNotificationParser.EXTRA_ACCOUNT_ID, "acc-1")
            putExtra(PushNotificationParser.EXTRA_SERVER, "https://mail.example.com")
            putExtra(PushNotificationParser.EXTRA_EMAIL, "user@example.com")
        }

        val target = PushNotificationParser.fromIntent(intent)

        assertNotNull(target)
        assertEquals("m-123", target?.messageId)
        assertEquals("acc-1", target?.accountId)
        assertEquals("https://mail.example.com", target?.server)
        assertEquals("user@example.com", target?.email)
    }

    @Test
    fun fromIntent_returnsNullWhenMessageIdMissing() {
        val intent = Intent().apply {
            putExtra(PushNotificationParser.EXTRA_ACCOUNT_ID, "acc-1")
        }

        assertNull(PushNotificationParser.fromIntent(intent))
        assertNull(PushNotificationParser.fromIntent(null))
    }
}
