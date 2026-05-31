package com.mobilemail.domain.repository

import com.mobilemail.data.common.Result
import com.mobilemail.domain.model.MessageListItem

data class SearchQuery(
    val query: String,
    val folderId: String? = null,
    val unreadOnly: Boolean = false,
    val hasAttachments: Boolean = false,
    val starredOnly: Boolean = false,
    val importantOnly: Boolean = false,
    val senderQuery: String = "",
    val limit: Int = 50
)

interface ISearchRepository {
    suspend fun searchMessages(searchQuery: SearchQuery): Result<List<MessageListItem>>
}
