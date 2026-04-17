package com.mobilemail.data.repository

import com.mobilemail.data.common.Result
import com.mobilemail.data.common.runCatchingSuspend
import com.mobilemail.data.jmap.JmapApi

class MessageActionsRepository(
    private val jmapClient: JmapApi
) {
    private fun requireSuccess(success: Boolean, actionName: String): Boolean {
        check(success) { "JMAP operation failed: $actionName" }
        return true
    }

    suspend fun updateKeywords(messageId: String, keywords: Map<String, Boolean>): Result<Boolean> = runCatchingSuspend {
        val session = jmapClient.getSession()
        val accountId = session.primaryAccounts?.mail 
            ?: session.accounts.keys.firstOrNull()
            ?: throw IllegalStateException("AccountId не найден")

        requireSuccess(
            success = jmapClient.updateEmailKeywords(messageId, keywords, accountId),
            actionName = "update keywords for $messageId"
        )
    }

    suspend fun markAsRead(messageId: String, isRead: Boolean): Result<Boolean> = runCatchingSuspend {
        updateKeywords(messageId, mapOf("\$seen" to isRead)).getOrThrow()
    }
    
    suspend fun toggleStarred(messageId: String, isStarred: Boolean): Result<Boolean> = runCatchingSuspend {
        updateKeywords(messageId, mapOf("\$flagged" to isStarred)).getOrThrow()
    }
    
    suspend fun deleteMessage(messageId: String): Result<Boolean> = runCatchingSuspend {
        val session = jmapClient.getSession()
        val accountId = session.primaryAccounts?.mail 
            ?: session.accounts.keys.firstOrNull()
            ?: throw IllegalStateException("AccountId не найден")
        
        requireSuccess(
            success = jmapClient.deleteEmail(messageId, accountId),
            actionName = "delete message $messageId"
        )
    }
    
    suspend fun moveMessage(
        messageId: String,
        fromMailboxId: String,
        toMailboxId: String
    ): Result<Boolean> = runCatchingSuspend {
        val session = jmapClient.getSession()
        val accountId = session.primaryAccounts?.mail 
            ?: session.accounts.keys.firstOrNull()
            ?: throw IllegalStateException("AccountId не найден")
        
        requireSuccess(
            success = jmapClient.moveEmail(messageId, fromMailboxId, toMailboxId, accountId),
            actionName = "move message $messageId"
        )
    }
}
