package com.mobilemail.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.mobilemail.data.local.database.AppDatabase

class MessagesViewModelFactory(
    private val server: String,
    private val email: String,
    private val password: String,
    private val accountId: String,
    private val database: AppDatabase? = null,
    private val application: android.app.Application? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MessagesViewModel::class.java)) {
            return MessagesViewModel(server, email, password, accountId, database, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
