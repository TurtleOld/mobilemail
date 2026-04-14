package com.mobilemail.ui.outbox

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class OutboxViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OutboxViewModel::class.java)) {
            return OutboxViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
