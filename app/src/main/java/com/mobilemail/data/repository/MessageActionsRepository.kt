package com.mobilemail.data.repository

import com.mobilemail.data.common.Result
import com.mobilemail.data.common.runCatchingSuspend
import com.mobilemail.data.jmap.JmapApi

class MessageActionsRepository(
    private val jmapClient: JmapApi
) {
    suspend fun updateKeywords(messageId: String, keywords: Map<String, Boolean>): Result<Boolean> = runCatchingSuspend {
        val session = jmapClient.getSession()
        val accountId = session.primaryAccounts?.mail 
            ?: session.accounts.keys.firstOrNull()
            ?: throw IllegalStateException("AccountId не найден")

        jmapClient.updateEmailKeywords(messageId, keywords, accountId)
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
        
        jmapClient.deleteEmail(messageId, accountId)
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
        
        jmapClient.moveEmail(messageId, fromMailboxId, toMailboxId, accountId)
    }
}
