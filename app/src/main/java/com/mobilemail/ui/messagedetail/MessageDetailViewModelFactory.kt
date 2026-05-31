package com.mobilemail.ui.messagedetail

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.mobilemail.data.jmap.MailClientFactory
import com.mobilemail.data.preferences.SavedSession
import com.mobilemail.data.repository.AttachmentRepository
import com.mobilemail.data.repository.MailRepository
import com.mobilemail.data.repository.MessageActionsRepository
import kotlinx.coroutines.runBlocking

class MessageDetailViewModelFactory(
    private val application: Application,
    private val session: SavedSession,
    private val messageId: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MessageDetailViewModel::class.java)) {
            val jmapClient = runBlocking {
                MailClientFactory.create(application, session.server, session.email, session.accountId)
            }
            val repository = MailRepository(jmapClient)
            val messageActionsRepository = MessageActionsRepository(jmapClient)
            val attachmentRepository = AttachmentRepository(jmapClient)
            return MessageDetailViewModel(
                application, session, messageId,
                repository, messageActionsRepository, attachmentRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
