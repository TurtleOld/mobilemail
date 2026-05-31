package com.mobilemail.ui.login

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test

class LoginScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<FragmentActivity>()

    @Test
    fun loginScreen_showsMainControls_andAllowsServerInput() {
        val application = ApplicationProvider.getApplicationContext<android.app.Application>()
        val viewModel = LoginViewModel(
            application = application,
            autoLoginEnabled = false
        )

        composeRule.setContent {
            MaterialTheme {
                LoginScreen(
                    viewModel = viewModel,
                    onLoginSuccess = { _, _, _, _ -> }
                )
            }
        }

        composeRule.onNodeWithText("Войти через OAuth")
            .assertIsDisplayed()
            .assertIsEnabled()

        composeRule.onNodeWithContentDescription("Адрес почтового сервера")
            .assertIsDisplayed()
            .performTextInput("mail.example.com")

        composeRule.onNodeWithText("mail.example.com")
            .assertIsDisplayed()
    }
}
