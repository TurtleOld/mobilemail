package com.mobilemail.data.repository

import com.mobilemail.data.common.Result
import com.mobilemail.data.common.runCatchingSuspend
import com.mobilemail.data.jmap.JmapApi
import com.mobilemail.data.model.EmailAddress
import com.mobilemail.data.model.MessageFlags
import com.mobilemail.data.model.MessageListItem
import com.mobilemail.domain.model.toDomain
import com.mobilemail.domain.repository.ISearchRepository
import com.mobilemail.domain.repository.SearchQuery
import java.time.Instant
import java.util.Date
import com.mobilemail.domain.model.MessageListItem as DomainMessageListItem

data class SearchParams(
    val query: String,
    val folderId: String? = null,
    val unreadOnly: Boolean = false,
    val hasAttachments: Boolean = false,
    val starredOnly: Boolean = false,
    val importantOnly: Boolean = false,
    val senderQuery: String = "",
    val dateRange: SearchRepository.DateRange = SearchRepository.DateRange.ANY
)

class SearchRepository(
    private val jmapClient: JmapApi
) : ISearchRepository {
    data class SearchPage(
        val items: List<DomainMessageListItem>,
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

    @Suppress("LongParameterList")
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
    ): Result<List<DomainMessageListItem>> = searchMessagesPage(
        params = SearchParams(
            query = query,
            folderId = folderId,
            unreadOnly = unreadOnly,
            hasAttachments = hasAttachments,
            starredOnly = starredOnly,
            importantOnly = importantOnly,
            senderQuery = senderQuery,
            dateRange = dateRange
        ),
        position = 0,
        limit = limit
    ).map { it.items }

    @Suppress("LongParameterList")
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
    ): Result<SearchPage> = searchMessagesPage(
        params = SearchParams(
            query = query,
            folderId = folderId,
            unreadOnly = unreadOnly,
            hasAttachments = hasAttachments,
            starredOnly = starredOnly,
            importantOnly = importantOnly,
            senderQuery = senderQuery,
            dateRange = dateRange
        ),
        position = position,
        limit = limit
    )

    suspend fun searchMessagesPage(
        params: SearchParams,
        position: Int = 0,
        limit: Int = 50
    ): Result<SearchPage> = runCatchingSuspend {
        val session = jmapClient.getSession()
        val accountId = session.primaryAccounts?.mail
            ?: session.accounts.keys.firstOrNull()
            ?: error("AccountId не найден")

        val filter = buildFilter(params)

        val queryResult = jmapClient.queryEmails(
            mailboxId = params.folderId,
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

        val items = mapEmailsToSearchItems(emails, params)
        SearchPage(
            items = items.map { it.toDomain() },
            nextPosition = (position + queryResult.ids.size).takeIf { queryResult.ids.size >= limit },
            hasMore = queryResult.ids.size >= limit
        )
    }

    private fun buildFilter(params: SearchParams): Map<String, Any>? {
        val trimmedQuery = params.query.trim()
        val conditions = mutableListOf<Map<String, Any>>()
        if (params.folderId != null) conditions += mapOf("inMailbox" to params.folderId)
        if (params.unreadOnly) conditions += mapOf("notKeyword" to "\$seen")
        if (params.hasAttachments) conditions += mapOf("hasAttachment" to true)
        if (params.starredOnly) conditions += mapOf("hasKeyword" to "\$flagged")
        if (params.importantOnly) conditions += mapOf("hasKeyword" to "\$important")
        if (trimmedQuery.isNotBlank()) conditions += mapOf("text" to trimmedQuery)
        if (params.senderQuery.isNotBlank()) conditions += mapOf("from" to params.senderQuery.trim())
        val dateFrom = dateRangeStart(params.dateRange)
        if (dateFrom != null) conditions += mapOf("after" to dateFrom)
        return when (conditions.size) {
            0 -> null
            1 -> conditions.first()
            else -> mapOf("operator" to "AND", "conditions" to conditions)
        }
    }

    private fun mapEmailsToSearchItems(
        emails: List<com.mobilemail.data.model.JmapEmail>,
        params: SearchParams
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
            val hasRealAttachments = resolveHasAttachments(email)

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
            matchesSender(message, params.senderQuery) &&
                matchesDateRange(message.date, params.dateRange) &&
                (!params.starredOnly || message.flags.starred) &&
                (!params.importantOnly || message.flags.important)
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

    // ISearchRepository

    override suspend fun searchMessages(searchQuery: SearchQuery): Result<List<DomainMessageListItem>> =
        searchMessages(
            query = searchQuery.query,
            folderId = searchQuery.folderId,
            unreadOnly = searchQuery.unreadOnly,
            hasAttachments = searchQuery.hasAttachments,
            starredOnly = searchQuery.starredOnly,
            importantOnly = searchQuery.importantOnly,
            senderQuery = searchQuery.senderQuery,
            dateRange = DateRange.ANY,
            limit = searchQuery.limit
        )
}
