package com.mobilemail.ui.newmessage

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.fragment.app.FragmentActivity
import org.junit.Rule
import org.junit.Test

class NewMessageScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<FragmentActivity>()

    @Test
    fun newMessageScreenContent_showsFieldsAndSendAction() {
        composeRule.setContent {
            MaterialTheme {
                NewMessageScreenContent(
                    email = "user@example.com",
                    to = "",
                    onToChange = {},
                    subject = "",
                    onSubjectChange = {},
                    body = "",
                    onBodyChange = {},
                    uiState = ComposeUiState(),
                    snackbarHostState = SnackbarHostState(),
                    onBack = {},
                    onPickAttachments = {},
                    onSend = {},
                    onRemoveAttachment = {}
                )
            }
        }

        composeRule.onNodeWithText("Новое письмо").assertIsDisplayed()
        composeRule.onNodeWithText("Кому").assertIsDisplayed()
        composeRule.onNodeWithText("Тема").assertIsDisplayed()
        composeRule.onNodeWithText("Сообщение").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Отправить").assertIsDisplayed()
    }
}
