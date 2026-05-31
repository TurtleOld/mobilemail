package com.mobilemail.ui.messages

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.mobilemail.data.jmap.MailClientFactory
import com.mobilemail.data.local.database.AppDatabase
import com.mobilemail.data.repository.MailRepository
import com.mobilemail.data.repository.MessageActionsRepository
import kotlinx.coroutines.runBlocking

class MessagesViewModelFactory(
    private val application: Application,
    private val server: String,
    private val email: String,
    private val accountId: String,
    private val database: AppDatabase? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MessagesViewModel::class.java)) {
            val jmapClient = runBlocking {
                MailClientFactory.create(application, server, email, accountId)
            }
            val repository = MailRepository(
                jmapClient = jmapClient,
                messageDao = database?.messageDao(),
                folderDao = database?.folderDao()
            )
            val messageActionsRepository = MessageActionsRepository(jmapClient)
            return MessagesViewModel(application, server, email, accountId, repository, messageActionsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
