package com.mobilemail.domain.repository

import com.mobilemail.data.common.Result
import com.mobilemail.domain.model.Account
import com.mobilemail.domain.model.Folder
import com.mobilemail.domain.model.MessageDetail
import com.mobilemail.domain.model.MessageListItem

interface IMailRepository {
    suspend fun getAccount(): Result<Account>
    suspend fun getFolders(): Result<List<Folder>>
    suspend fun getMessages(folderId: String, position: Int = 0, limit: Int = 50): Result<List<MessageListItem>>
    suspend fun getMessage(messageId: String): Result<MessageDetail>
    suspend fun getThreadMessages(threadId: String, limit: Int = 100): Result<List<MessageListItem>>
    suspend fun getThreadDetails(threadId: String, limit: Int = 100): Result<List<MessageDetail>>
    suspend fun updateMessageReadStatus(messageId: String, isUnread: Boolean)
}
