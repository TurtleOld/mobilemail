package com.mobilemail.data.repository

import com.mobilemail.data.common.Result
import com.mobilemail.data.common.runCatchingSuspend
import com.mobilemail.data.jmap.JmapApi
import com.mobilemail.domain.repository.IMessageActionsRepository

class MessageActionsRepository(
    private val jmapClient: JmapApi
) : IMessageActionsRepository {
    private fun requireSuccess(success: Boolean, actionName: String): Boolean {
        check(success) { "JMAP operation failed: $actionName" }
        return true
    }

    override suspend fun updateKeywords(messageId: String, keywords: Map<String, Boolean>): Result<Boolean> = runCatchingSuspend {
        val session = jmapClient.getSession()
        val accountId = session.primaryAccounts?.mail 
            ?: session.accounts.keys.firstOrNull()
            ?: error("AccountId не найден")

        requireSuccess(
            success = jmapClient.updateEmailKeywords(messageId, keywords, accountId),
            actionName = "update keywords for $messageId"
        )
    }

    override suspend fun markAsRead(messageId: String, isRead: Boolean): Result<Boolean> = runCatchingSuspend {
        updateKeywords(messageId, mapOf("\$seen" to isRead)).getOrThrow()
    }
    
    override suspend fun toggleStarred(messageId: String, isStarred: Boolean): Result<Boolean> = runCatchingSuspend {
        updateKeywords(messageId, mapOf("\$flagged" to isStarred)).getOrThrow()
    }
    
    override suspend fun deleteMessage(messageId: String): Result<Boolean> = runCatchingSuspend {
        val session = jmapClient.getSession()
        val accountId = session.primaryAccounts?.mail 
            ?: session.accounts.keys.firstOrNull()
            ?: error("AccountId не найден")
        
        requireSuccess(
            success = jmapClient.deleteEmail(messageId, accountId),
            actionName = "delete message $messageId"
        )
    }
    
    override suspend fun moveMessage(
        messageId: String,
        fromMailboxId: String,
        toMailboxId: String
    ): Result<Boolean> = runCatchingSuspend {
        val session = jmapClient.getSession()
        val accountId = session.primaryAccounts?.mail 
            ?: session.accounts.keys.firstOrNull()
            ?: error("AccountId не найден")
        
        requireSuccess(
            success = jmapClient.moveEmail(messageId, fromMailboxId, toMailboxId, accountId),
            actionName = "move message $messageId"
        )
    }
}
