package com.mobilemail.domain.repository

import com.mobilemail.data.common.Result

interface IMessageActionsRepository {
    suspend fun markAsRead(messageId: String, isRead: Boolean): Result<Boolean>
    suspend fun toggleStarred(messageId: String, isStarred: Boolean): Result<Boolean>
    suspend fun deleteMessage(messageId: String): Result<Boolean>
    suspend fun moveMessage(messageId: String, fromMailboxId: String, toMailboxId: String): Result<Boolean>
    suspend fun updateKeywords(messageId: String, keywords: Map<String, Boolean>): Result<Boolean>
}
