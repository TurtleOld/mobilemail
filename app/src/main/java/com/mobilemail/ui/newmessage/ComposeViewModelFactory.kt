package com.mobilemail.ui.newmessage

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class ComposeViewModelFactory(
    private val application: Application,
    private val server: String,
    private val email: String,
    private val password: String,
    private val accountId: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ComposeViewModel::class.java)) {
            return ComposeViewModel(application, server, email, password, accountId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}