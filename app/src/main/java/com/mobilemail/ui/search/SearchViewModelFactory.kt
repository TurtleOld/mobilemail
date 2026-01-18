package com.mobilemail.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class SearchViewModelFactory(
    private val server: String,
    private val email: String,
    private val password: String,
    private val accountId: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {
            return SearchViewModel(server, email, password, accountId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
