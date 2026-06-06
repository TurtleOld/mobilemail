package com.mobilemail.ui.messagedetail

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.mobilemail.data.preferences.SavedSession

class MessageDetailViewModelFactory(
    private val application: Application,
    private val session: SavedSession,
    private val messageId: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MessageDetailViewModel::class.java)) {
            return MessageDetailViewModel(application, session, messageId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
