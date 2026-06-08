package com.mobilemail.ui.search

import android.app.Application
import com.mobilemail.data.local.database.AppDatabase
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class SearchViewModelFactory(
    private val application: Application,
    private val server: String,
    private val email: String,
    private val accountId: String,
    private val database: AppDatabase? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {
            return SearchViewModel(application, server, email, accountId, database) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
