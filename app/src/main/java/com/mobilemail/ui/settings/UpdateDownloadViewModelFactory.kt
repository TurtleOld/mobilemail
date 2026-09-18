package com.mobilemail.ui.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class UpdateDownloadViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UpdateDownloadViewModel::class.java)) {
            return UpdateDownloadViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
