package com.mobilemail.data.repository

import com.mobilemail.data.common.Result
import com.mobilemail.data.common.runCatchingSuspend
import com.mobilemail.data.jmap.JmapClient

class MessageActionsRepository(
    private val jmapClient: JmapClient
) {
    suspend fun markAsRead(messageId: String, isRead: Boolean): Result<Boolean> = runCatchingSuspend {
        val session = jmapClient.getSession()
        val accountId = session.primaryAccounts?.mail 
            ?: session.accounts.keys.firstOrNull()
            ?: throw IllegalStateException("AccountId не найден")
        
        val keywords = mapOf("\$seen" to isRead)
        jmapClient.updateEmailKeywords(messageId, keywords, accountId)
    }
    
    suspend fun toggleStarred(messageId: String, isStarred: Boolean): Result<Boolean> = runCatchingSuspend {
        val session = jmapClient.getSession()
        val accountId = session.primaryAccounts?.mail 
            ?: session.accounts.keys.firstOrNull()
            ?: throw IllegalStateException("AccountId не найден")
        
        val keywords = mapOf("\$flagged" to isStarred)
        jmapClient.updateEmailKeywords(messageId, keywords, accountId)
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
