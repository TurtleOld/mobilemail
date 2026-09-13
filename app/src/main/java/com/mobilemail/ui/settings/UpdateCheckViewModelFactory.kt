package com.mobilemail.ui.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class UpdateCheckViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UpdateCheckViewModel::class.java)) {
            return UpdateCheckViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
