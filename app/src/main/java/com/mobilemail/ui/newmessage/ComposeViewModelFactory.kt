package com.mobilemail.ui.newmessage

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.mobilemail.data.jmap.MailClientFactory
import com.mobilemail.data.repository.ComposeRepository
import kotlinx.coroutines.runBlocking

class ComposeViewModelFactory(
    private val application: Application,
    private val server: String,
    private val email: String,
    private val accountId: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ComposeViewModel::class.java)) {
            val jmapClient = runBlocking {
                MailClientFactory.create(application, server, email, accountId)
            }
            val repository = ComposeRepository(jmapClient)
            return ComposeViewModel(application, server, email, accountId, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
