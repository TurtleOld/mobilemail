package com.mobilemail.ui.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilemail.data.jmap.JmapClient
import com.mobilemail.data.model.Folder
import com.mobilemail.data.model.MessageListItem
import com.mobilemail.data.repository.MailRepository
import com.mobilemail.data.repository.SearchRepository
import com.mobilemail.ui.common.AppError
import com.mobilemail.ui.common.ErrorMapper
import com.mobilemail.data.common.fold
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val selectedFolder: Folder? = null,
    val folders: List<Folder> = emptyList(),
    val unreadOnly: Boolean = false,
    val hasAttachments: Boolean = false,
    val results: List<MessageListItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: AppError? = null
)

class SearchViewModel(
    private val server: String,
    private val email: String,
    private val password: String,
    private val accountId: String
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState

    private val jmapClient = JmapClient.getOrCreate(server, email, password, accountId)
    private val mailRepository = MailRepository(jmapClient)
    private val searchRepository = SearchRepository(jmapClient)

    init {
        loadFolders()
    }

    private fun loadFolders() {
        viewModelScope.launch {
            mailRepository.getFolders().fold(
                onError = { e ->
                    Log.e("SearchViewModel", "Ошибка загрузки папок", e)
                    _uiState.value = _uiState.value.copy(
                        error = ErrorMapper.mapException(e)
                    )
                },
                onSuccess = { folders ->
                    Log.d("SearchViewModel", "Получено папок: ${folders.size}")
                    _uiState.value = _uiState.value.copy(folders = folders)
                }
            )
        }
    }

    fun updateQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }

    fun selectFolder(folder: Folder?) {
        _uiState.value = _uiState.value.copy(selectedFolder = folder)
    }

    fun toggleUnreadOnly() {
        _uiState.value = _uiState.value.copy(unreadOnly = !_uiState.value.unreadOnly)
    }

    fun toggleHasAttachments() {
        _uiState.value = _uiState.value.copy(hasAttachments = !_uiState.value.hasAttachments)
    }

    fun performSearch() {
        val currentQuery = _uiState.value.query.trim()
        if (currentQuery.isBlank()) {
            _uiState.value = _uiState.value.copy(results = emptyList())
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            searchRepository.searchMessages(
                query = currentQuery,
                folderId = _uiState.value.selectedFolder?.id,
                unreadOnly = _uiState.value.unreadOnly,
                hasAttachments = _uiState.value.hasAttachments
            ).fold(
                onError = { e ->
                    Log.e("SearchViewModel", "Ошибка поиска", e)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = ErrorMapper.mapException(e),
                        results = emptyList()
                    )
                },
                onSuccess = { results ->
                    Log.d("SearchViewModel", "Найдено результатов: ${results.size}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        results = results
                    )
                }
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
