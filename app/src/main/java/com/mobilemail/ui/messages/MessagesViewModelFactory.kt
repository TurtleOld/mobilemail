package com.mobilemail.ui.messages

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.mobilemail.data.local.database.AppDatabase

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
            return MessagesViewModel(application, server, email, accountId, database) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
