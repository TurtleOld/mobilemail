package com.mobilemail.domain.repository

import com.mobilemail.data.common.Result
import com.mobilemail.domain.model.MessageListItem

interface ISearchRepository {
    suspend fun searchMessages(
        query: String,
        folderId: String? = null,
        unreadOnly: Boolean = false,
        hasAttachments: Boolean = false,
        starredOnly: Boolean = false,
        importantOnly: Boolean = false,
        senderQuery: String = "",
        limit: Int = 50
    ): Result<List<MessageListItem>>
}
