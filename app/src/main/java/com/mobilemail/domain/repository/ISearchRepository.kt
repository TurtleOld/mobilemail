package com.mobilemail.domain.repository

import com.mobilemail.domain.common.Result
import com.mobilemail.domain.model.MessageListItem

enum class DateRange {
    ANY,
    TODAY,
    LAST_7_DAYS,
    LAST_30_DAYS,
    LAST_365_DAYS
}

data class SearchQuery(
    val query: String,
    val folderId: String? = null,
    val unreadOnly: Boolean = false,
    val hasAttachments: Boolean = false,
    val starredOnly: Boolean = false,
    val importantOnly: Boolean = false,
    val senderQuery: String = "",
    val dateRange: DateRange = DateRange.ANY,
    val limit: Int = 50
)

interface ISearchRepository {
    suspend fun searchMessages(searchQuery: SearchQuery): Result<List<MessageListItem>>
}
