package com.mobilemail.ui.security

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test

class PinSetupScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<FragmentActivity>()

    @Test
    fun pinSetupScreen_showsMainControls() {
        val application = ApplicationProvider.getApplicationContext<android.app.Application>()
        val viewModel = PinSetupViewModel(application)

        composeRule.setContent {
            MaterialTheme {
                PinSetupScreen(
                    viewModel = viewModel,
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithText("Настройки входа").assertIsDisplayed()
        composeRule.onNodeWithText("Вход по PIN-коду").assertIsDisplayed()
        composeRule.onNodeWithText("Защитите приложение PIN-кодом").assertIsDisplayed()
    }
}
