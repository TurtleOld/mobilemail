package com.mobilemail.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ApplicationProvider
import com.mobilemail.data.preferences.PreferencesManager
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<FragmentActivity>()

    @Test
    fun settingsScreen_showsMainSections() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val preferencesManager = PreferencesManager(context)

        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    server = "https://mail.example.com",
                    email = "user@example.com",
                    preferencesManager = preferencesManager,
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithText("Настройки").assertIsDisplayed()
        composeRule.onNodeWithText("Безопасность").assertIsDisplayed()
        composeRule.onNodeWithText("Конфиденциальность").assertIsDisplayed()
        composeRule.onNodeWithText("Очищать кэш при выходе").assertIsDisplayed()
        composeRule.onNodeWithText("Подпись").assertIsDisplayed()
        composeRule.onNodeWithText("Сохранить").assertIsDisplayed()
    }
}
