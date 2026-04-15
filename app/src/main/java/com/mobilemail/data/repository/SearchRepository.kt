package com.mobilemail.data.repository

import com.mobilemail.data.common.Result
import com.mobilemail.data.common.runCatchingSuspend
import com.mobilemail.data.jmap.JmapApi
import com.mobilemail.data.model.EmailAddress
import com.mobilemail.data.model.MessageFlags
import com.mobilemail.data.model.MessageListItem
import java.time.Instant
import java.util.Date

class SearchRepository(
    private val jmapClient: JmapApi
) {
    data class SearchPage(
        val items: List<MessageListItem>,
        val nextPosition: Int?,
        val hasMore: Boolean
    )

    enum class DateRange {
        ANY,
        TODAY,
        LAST_7_DAYS,
        LAST_30_DAYS,
        LAST_365_DAYS
    }

    suspend fun searchMessages(
        query: String,
        folderId: String? = null,
        unreadOnly: Boolean = false,
        hasAttachments: Boolean = false,
        starredOnly: Boolean = false,
        importantOnly: Boolean = false,
        senderQuery: String = "",
        dateRange: DateRange = DateRange.ANY,
        limit: Int = 50
    ): Result<List<MessageListItem>> = searchMessagesPage(
        query = query,
        folderId = folderId,
        unreadOnly = unreadOnly,
        hasAttachments = hasAttachments,
        starredOnly = starredOnly,
        importantOnly = importantOnly,
        senderQuery = senderQuery,
        dateRange = dateRange,
        position = 0,
        limit = limit
    ).map { it.items }

    suspend fun searchMessagesPage(
        query: String,
        folderId: String? = null,
        unreadOnly: Boolean = false,
        hasAttachments: Boolean = false,
        starredOnly: Boolean = false,
        importantOnly: Boolean = false,
        senderQuery: String = "",
        dateRange: DateRange = DateRange.ANY,
        position: Int = 0,
        limit: Int = 50
    ): Result<SearchPage> = runCatchingSuspend {
        val session = jmapClient.getSession()
        val accountId = session.primaryAccounts?.mail 
            ?: session.accounts.keys.firstOrNull()
            ?: throw IllegalStateException("AccountId не найден")
        
        val trimmedQuery = query.trim()
        val conditions = mutableListOf<Map<String, Any>>()
        if (folderId != null) {
            conditions += mapOf("inMailbox" to folderId)
        }
        if (unreadOnly) {
            conditions += mapOf("notKeyword" to "\$seen")
        }
        if (hasAttachments) {
            conditions += mapOf("hasAttachment" to true)
        }
        if (starredOnly) {
            conditions += mapOf("hasKeyword" to "\$flagged")
        }
        if (importantOnly) {
            conditions += mapOf("hasKeyword" to "\$important")
        }
        if (trimmedQuery.isNotBlank()) {
            conditions += mapOf("text" to trimmedQuery)
        }
        if (senderQuery.isNotBlank()) {
            conditions += mapOf("from" to senderQuery.trim())
        }
        val dateFrom = dateRangeStart(dateRange)
        if (dateFrom != null) {
            conditions += mapOf("after" to dateFrom)
        }
        val filter = when (conditions.size) {
            0 -> null
            1 -> conditions.first()
            else -> mapOf("operator" to "AND", "conditions" to conditions)
        }

        val queryResult = jmapClient.queryEmails(
            mailboxId = folderId,
            accountId = accountId,
            position = position,
            limit = limit,
            filter = filter,
            searchText = null
        )
        
        if (queryResult.ids.isEmpty()) {
            return@runCatchingSuspend SearchPage(emptyList(), null, false)
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
        
        val items = emails.map { email ->
            val from = email.from?.firstOrNull() ?: EmailAddress(email = "unknown")
            val isUnread = email.keywords?.get("\$seen") != true
            val isStarred = email.keywords?.get("\$flagged") == true
            val isImportant = email.keywords?.get("\$important") == true
            val receivedDate = try {
                Date.from(Instant.parse(email.receivedAt))
            } catch (e: Exception) {
                Date()
            }
            
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
                date = receivedDate,
                flags = MessageFlags(
                    unread = isUnread,
                    starred = isStarred,
                    important = isImportant,
                    hasAttachments = hasRealAttachments
                ),
                size = email.size
            )
        }.filter { message ->
            matchesSender(message, senderQuery) &&
                matchesDateRange(message.date, dateRange) &&
                (!starredOnly || message.flags.starred) &&
                (!importantOnly || message.flags.important)
        }
        SearchPage(
            items = items,
            nextPosition = (position + queryResult.ids.size).takeIf { queryResult.ids.size >= limit },
            hasMore = queryResult.ids.size >= limit
        )
    }

    private fun matchesSender(message: MessageListItem, senderQuery: String): Boolean {
        if (senderQuery.isBlank()) return true
        val normalized = senderQuery.trim().lowercase()
        return (message.from.name ?: "").lowercase().contains(normalized) ||
            message.from.email.lowercase().contains(normalized)
    }

    private fun dateRangeStart(dateRange: DateRange): String? {
        if (dateRange == DateRange.ANY) return null
        val day = 24L * 60L * 60L * 1000L
        val now = System.currentTimeMillis()
        val fromMillis = when (dateRange) {
            DateRange.ANY -> return null
            DateRange.TODAY -> now - day
            DateRange.LAST_7_DAYS -> now - day * 7
            DateRange.LAST_30_DAYS -> now - day * 30
            DateRange.LAST_365_DAYS -> now - day * 365
        }
        return Instant.ofEpochMilli(fromMillis).toString()
    }

    private fun matchesDateRange(date: Date, dateRange: DateRange): Boolean {
        if (dateRange == DateRange.ANY) return true
        val now = System.currentTimeMillis()
        val diff = now - date.time
        val day = 24L * 60L * 60L * 1000L
        return when (dateRange) {
            DateRange.ANY -> true
            DateRange.TODAY -> diff <= day
            DateRange.LAST_7_DAYS -> diff <= day * 7
            DateRange.LAST_30_DAYS -> diff <= day * 30
            DateRange.LAST_365_DAYS -> diff <= day * 365
        }
    }
}
