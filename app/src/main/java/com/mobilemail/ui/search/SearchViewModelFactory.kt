package com.mobilemail.ui.search

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.mobilemail.data.jmap.MailClientFactory
import com.mobilemail.data.repository.MailRepository
import com.mobilemail.data.repository.SearchRepository
import kotlinx.coroutines.runBlocking

class SearchViewModelFactory(
    private val application: Application,
    private val server: String,
    private val email: String,
    private val accountId: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {
            val jmapClient = runBlocking {
                MailClientFactory.create(application, server, email, accountId)
            }
            val mailRepository = MailRepository(jmapClient)
            val searchRepository = SearchRepository(jmapClient)
            return SearchViewModel(mailRepository, searchRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
