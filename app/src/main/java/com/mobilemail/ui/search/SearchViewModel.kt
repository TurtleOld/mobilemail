package com.mobilemail.ui.search

import android.app.Application
import android.util.Log
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mobilemail.data.jmap.MailClientFactory
import com.mobilemail.domain.model.Folder
import com.mobilemail.domain.model.FolderRole
import com.mobilemail.domain.model.MessageListItem
import com.mobilemail.data.paging.SearchPagingSource
import com.mobilemail.data.repository.MailRepository
import com.mobilemail.data.repository.SearchRepository
import com.mobilemail.domain.repository.DateRange
import com.mobilemail.domain.repository.SearchQuery
import com.mobilemail.ui.common.AppError
import com.mobilemail.ui.common.FeatureScreenUiState
import com.mobilemail.ui.common.NotificationState
import com.mobilemail.ui.common.ErrorMapper
import com.mobilemail.data.common.fold
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

enum class SearchSmartFilter(
    val label: String,
    val dateRange: DateRange = DateRange.ANY,
    val unreadOnly: Boolean = false,
    val hasAttachments: Boolean = false,
    val starredOnly: Boolean = false,
    val importantOnly: Boolean = false
) {
    RECENT("7 дней", dateRange = DateRange.LAST_7_DAYS),
    UNREAD("Непрочитанные", unreadOnly = true),
    ATTACHMENTS("Вложения", hasAttachments = true),
    STARRED("Избранное", starredOnly = true),
    IMPORTANT("Важные", importantOnly = true)
}

data class SearchUiState(
    val query: String = "",
    val senderQuery: String = "",
    val selectedFolder: Folder? = null,
    val folders: List<Folder> = emptyList(),
    val unreadOnly: Boolean = false,
    val hasAttachments: Boolean = false,
    val starredOnly: Boolean = false,
    val importantOnly: Boolean = false,
    val dateRange: DateRange = DateRange.ANY,
    val showAdvancedFilters: Boolean = false,
    val hasSearched: Boolean = false,
    val isLoading: Boolean = false,
    override val error: AppError? = null,
    override val notification: NotificationState = NotificationState.None,
) : FeatureScreenUiState {
    val hasActiveFilters: Boolean
        get() = selectedFolder != null || unreadOnly || hasAttachments || starredOnly || importantOnly ||
            dateRange != DateRange.ANY || senderQuery.isNotBlank()
}

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(
    application: Application,
    private val server: String,
    private val email: String,
    private val accountId: String
) : AndroidViewModel(application) {

    private var mailRepository: MailRepository? = null
    private var searchRepository: SearchRepository? = null
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState

    private val activeSearchQuery = MutableStateFlow<SearchQuery?>(null)

    val pagedResults: Flow<PagingData<MessageListItem>> = activeSearchQuery
        .flatMapLatest { query ->
            val repo = searchRepository
            if (query == null || repo == null) {
                flowOf(PagingData.empty())
            } else {
                Pager(
                    config = PagingConfig(
                        pageSize = 25,
                        initialLoadSize = 50,
                        prefetchDistance = 10,
                        enablePlaceholders = false
                    ),
                    pagingSourceFactory = { SearchPagingSource(repo, query) }
                ).flow
            }
        }
        .cachedIn(viewModelScope)

    init {
        viewModelScope.launch {
            try {
                val jmapClient = MailClientFactory.create(getApplication(), server, email, accountId)
                mailRepository = MailRepository(jmapClient)
                searchRepository = SearchRepository(jmapClient)
            } catch (e: Exception) {
                Log.e("SearchViewModel", "Ошибка инициализации клиента", e)
                _uiState.value = _uiState.value.copy(error = ErrorMapper.mapException(e))
                return@launch
            }
            loadFolders()
        }
    }

    private fun loadFolders() {
        viewModelScope.launch {
            mailRepository?.getFolders()?.fold(
                onError = { e ->
                    Log.e("SearchViewModel", "Ошибка загрузки папок", e)
                    _uiState.value = _uiState.value.copy(
                        error = ErrorMapper.mapException(e)
                    )
                },
                onSuccess = { folders ->
                    Log.d("SearchViewModel", "Получено папок: ${folders.size}")
                    val defaultOrder = listOf(
                        FolderRole.INBOX,
                        FolderRole.DRAFTS,
                        FolderRole.SENT,
                        FolderRole.SPAM,
                        FolderRole.TRASH
                    )
                    val defaultFolders = defaultOrder.mapNotNull { role ->
                        folders.firstOrNull { it.role == role }
                    }
                    val customFolders = folders
                        .filter { it.role !in defaultOrder }
                        .sortedBy { it.name.lowercase() }
                    _uiState.value = _uiState.value.copy(
                        folders = defaultFolders + customFolders
                    )
                }
            )
        }
    }

    fun updateQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }

    fun updateSenderQuery(query: String) {
        _uiState.value = _uiState.value.copy(senderQuery = query)
        refreshIfNeeded()
    }

    fun selectFolder(folder: Folder?) {
        _uiState.value = _uiState.value.copy(selectedFolder = folder)
        refreshIfNeeded()
    }

    fun toggleUnreadOnly() {
        _uiState.value = _uiState.value.copy(unreadOnly = !_uiState.value.unreadOnly)
        refreshIfNeeded()
    }

    fun toggleHasAttachments() {
        _uiState.value = _uiState.value.copy(hasAttachments = !_uiState.value.hasAttachments)
        refreshIfNeeded()
    }

    fun toggleStarredOnly() {
        _uiState.value = _uiState.value.copy(starredOnly = !_uiState.value.starredOnly)
        refreshIfNeeded()
    }

    fun toggleImportantOnly() {
        _uiState.value = _uiState.value.copy(importantOnly = !_uiState.value.importantOnly)
        refreshIfNeeded()
    }

    fun setDateRange(range: DateRange) {
        _uiState.value = _uiState.value.copy(dateRange = range)
        refreshIfNeeded()
    }

    fun toggleAdvancedFilters() {
        _uiState.value = _uiState.value.copy(showAdvancedFilters = !_uiState.value.showAdvancedFilters)
    }

    fun clearFilters() {
        _uiState.value = _uiState.value.copy(
            senderQuery = "",
            selectedFolder = null,
            unreadOnly = false,
            hasAttachments = false,
            starredOnly = false,
            importantOnly = false,
            dateRange = DateRange.ANY
        )
        refreshIfNeeded()
    }

    fun applySmartFilter(filter: SearchSmartFilter) {
        _uiState.value = _uiState.value.copy(
            unreadOnly = filter.unreadOnly,
            hasAttachments = filter.hasAttachments,
            starredOnly = filter.starredOnly,
            importantOnly = filter.importantOnly,
            dateRange = filter.dateRange
        )
        refreshIfNeeded()
    }

    fun performSearch() {
        val currentQuery = _uiState.value.query.trim()
        if (currentQuery.isBlank() && !_uiState.value.hasActiveFilters) {
            activeSearchQuery.value = null
            _uiState.value = _uiState.value.copy(hasSearched = false)
            return
        }

        activeSearchQuery.value = SearchQuery(
            query = currentQuery,
            senderQuery = _uiState.value.senderQuery,
            folderId = _uiState.value.selectedFolder?.id,
            unreadOnly = _uiState.value.unreadOnly,
            hasAttachments = _uiState.value.hasAttachments,
            starredOnly = _uiState.value.starredOnly,
            importantOnly = _uiState.value.importantOnly,
            dateRange = _uiState.value.dateRange
        )
        _uiState.value = _uiState.value.copy(hasSearched = true, error = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearNotification() {
        _uiState.value = _uiState.value.copy(notification = NotificationState.None)
    }

    private fun refreshIfNeeded() {
        if (_uiState.value.query.isNotBlank() || _uiState.value.hasActiveFilters) {
            performSearch()
        }
    }
}
