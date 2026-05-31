package com.mobilemail.data.repository

import com.mobilemail.data.common.Result
import com.mobilemail.data.common.runCatchingSuspend
import com.mobilemail.data.jmap.JmapApi
import com.mobilemail.data.model.EmailAddress
import com.mobilemail.data.model.MessageFlags
import com.mobilemail.data.model.MessageListItem
import com.mobilemail.domain.model.toDomain
import com.mobilemail.domain.repository.DateRange
import com.mobilemail.domain.repository.ISearchRepository
import com.mobilemail.domain.repository.SearchQuery
import java.time.Instant
import java.util.Date
import com.mobilemail.domain.model.MessageListItem as DomainMessageListItem

data class SearchPage(
    val items: List<DomainMessageListItem>,
    val nextPosition: Int?,
    val hasMore: Boolean
)

class SearchRepository(
    private val jmapClient: JmapApi
) : ISearchRepository {

    suspend fun searchMessagesPage(
        searchQuery: SearchQuery,
        position: Int = 0,
        limit: Int = 50
    ): Result<SearchPage> = runCatchingSuspend {
        val session = jmapClient.getSession()
        val accountId = session.primaryAccounts?.mail
            ?: session.accounts.keys.firstOrNull()
            ?: error("AccountId не найден")

        val filter = buildFilter(searchQuery)

        val queryResult = jmapClient.queryEmails(
            mailboxId = searchQuery.folderId,
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

        val items = mapEmailsToSearchItems(emails, searchQuery)
        SearchPage(
            items = items.map { it.toDomain() },
            nextPosition = (position + queryResult.ids.size).takeIf { queryResult.ids.size >= limit },
            hasMore = queryResult.ids.size >= limit
        )
    }

    private fun buildFilter(searchQuery: SearchQuery): Map<String, Any>? {
        val trimmedQuery = searchQuery.query.trim()
        val conditions = mutableListOf<Map<String, Any>>()
        if (searchQuery.folderId != null) conditions += mapOf("inMailbox" to searchQuery.folderId)
        if (searchQuery.unreadOnly) conditions += mapOf("notKeyword" to "\$seen")
        if (searchQuery.hasAttachments) conditions += mapOf("hasAttachment" to true)
        if (searchQuery.starredOnly) conditions += mapOf("hasKeyword" to "\$flagged")
        if (searchQuery.importantOnly) conditions += mapOf("hasKeyword" to "\$important")
        if (trimmedQuery.isNotBlank()) conditions += mapOf("text" to trimmedQuery)
        if (searchQuery.senderQuery.isNotBlank()) conditions += mapOf("from" to searchQuery.senderQuery.trim())
        val dateFrom = dateRangeStart(searchQuery.dateRange)
        if (dateFrom != null) conditions += mapOf("after" to dateFrom)
        return when (conditions.size) {
            0 -> null
            1 -> conditions.first()
            else -> mapOf("operator" to "AND", "conditions" to conditions)
        }
    }

    private fun mapEmailsToSearchItems(
        emails: List<com.mobilemail.data.model.JmapEmail>,
        searchQuery: SearchQuery
    ): List<MessageListItem> {
        return emails.map { email ->
            val from = email.from?.firstOrNull() ?: EmailAddress(email = "unknown")
            val isUnread = email.keywords?.get("\$seen") != true
            val isStarred = email.keywords?.get("\$flagged") == true
            val isImportant = email.keywords?.get("\$important") == true
            val receivedDate = try {
                Date.from(Instant.parse(email.receivedAt))
            } catch (e: Exception) {
                Date()
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
                    hasAttachments = resolveHasAttachments(email)
                ),
                size = email.size
            )
        }.filter { message ->
            matchesSender(message, searchQuery.senderQuery) &&
                matchesDateRange(message.date, searchQuery.dateRange) &&
                (!searchQuery.starredOnly || message.flags.starred) &&
                (!searchQuery.importantOnly || message.flags.important)
        }
    }

    private fun resolveHasAttachments(email: com.mobilemail.data.model.JmapEmail): Boolean {
        if (email.bodyStructure == null) return email.hasAttachment == true
        return try {
            val bodyStructureJson = when (email.bodyStructure) {
                is org.json.JSONObject -> email.bodyStructure
                is org.json.JSONArray -> email.bodyStructure
                else -> null
            }
            AttachmentParser.parseAttachments(bodyStructureJson).isNotEmpty()
        } catch (e: Exception) {
            android.util.Log.e("SearchRepository", "Ошибка парсинга вложений для поиска", e)
            email.hasAttachment == true
        }
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

    override suspend fun searchMessages(searchQuery: SearchQuery): Result<List<DomainMessageListItem>> =
        searchMessagesPage(searchQuery, limit = searchQuery.limit).map { it.items }
}
