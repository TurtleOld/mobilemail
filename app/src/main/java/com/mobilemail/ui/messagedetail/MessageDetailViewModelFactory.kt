package com.mobilemail.ui.messagedetail

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class MessageDetailViewModelFactory(
    private val application: Application,
    private val server: String,
    private val email: String,
    private val password: String,
    private val accountId: String,
    private val messageId: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MessageDetailViewModel::class.java)) {
            return MessageDetailViewModel(application, server, email, password, accountId, messageId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
