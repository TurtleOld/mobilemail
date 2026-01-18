package com.mobilemail.data.repository

import com.mobilemail.data.common.Result
import com.mobilemail.data.common.runCatchingSuspend
import com.mobilemail.data.jmap.JmapClient
import com.mobilemail.data.model.EmailAddress
import com.mobilemail.data.model.MessageFlags
import com.mobilemail.data.model.MessageListItem
import java.time.Instant
import java.util.Date

class SearchRepository(
    private val jmapClient: JmapClient
) {
    suspend fun searchMessages(
        query: String,
        folderId: String? = null,
        unreadOnly: Boolean = false,
        hasAttachments: Boolean = false,
        limit: Int = 50
    ): Result<List<MessageListItem>> = runCatchingSuspend {
        val session = jmapClient.getSession()
        val accountId = session.primaryAccounts?.mail 
            ?: session.accounts.keys.firstOrNull()
            ?: throw IllegalStateException("AccountId не найден")
        
        val filter = mutableMapOf<String, Any>()
        if (folderId != null) {
            filter["inMailbox"] = folderId
        }
        if (unreadOnly) {
            filter["notKeyword"] = "\$seen"
        }
        if (hasAttachments) {
            filter["hasAttachment"] = true
        }
        
        val queryResult = jmapClient.queryEmails(
            mailboxId = folderId,
            accountId = accountId,
            position = 0,
            limit = limit,
            filter = filter,
            searchText = query
        )
        
        if (queryResult.ids.isEmpty()) {
            return@runCatchingSuspend emptyList()
        }
        
        val emails = jmapClient.getEmails(
            ids = queryResult.ids,
            accountId = accountId,
            properties = listOf(
                "id", "threadId", "mailboxIds", "from", "to", "subject",
                "receivedAt", "preview", "hasAttachment",
                "size", "keywords"
            )
        )
        
        emails.map { email ->
            val from = email.from?.firstOrNull() ?: EmailAddress(email = "unknown")
            val isUnread = email.keywords?.get("\$seen") != true
            val isStarred = email.keywords?.get("\$flagged") == true
            val isImportant = email.keywords?.get("\$important") == true
            
            // Парсим вложения для точного определения их наличия
            val hasRealAttachments = if (email.bodyStructure != null) {
                try {
                    val bodyStructureJson = when (email.bodyStructure) {
                        is org.json.JSONObject -> email.bodyStructure
                        is org.json.JSONArray -> email.bodyStructure
                        else -> null
                    }
                    val parsedAttachments = com.mobilemail.data.repository.AttachmentParser.parseAttachments(bodyStructureJson)
                    parsedAttachments.isNotEmpty()
                } catch (e: Exception) {
                    android.util.Log.e("SearchRepository", "Ошибка парсинга вложений для поиска", e)
                    email.hasAttachment == true
                }
            } else {
                email.hasAttachment == true
            }
            
            MessageListItem(
                id = email.id,
                threadId = email.threadId,
                from = from,
                subject = email.subject ?: "(без темы)",
                snippet = email.preview ?: "",
                date = try {
                    Date.from(Instant.parse(email.receivedAt))
                } catch (e: Exception) {
                    Date()
                },
                flags = MessageFlags(
                    unread = isUnread,
                    starred = isStarred,
                    important = isImportant,
                    hasAttachments = hasRealAttachments
                ),
                size = email.size
            )
        }
    }
}
