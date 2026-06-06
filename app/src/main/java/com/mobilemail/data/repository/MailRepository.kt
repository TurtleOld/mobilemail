package com.mobilemail.data.repository

import android.util.Log
import com.mobilemail.data.common.Result
import com.mobilemail.data.common.runCatchingSuspend
import com.mobilemail.data.jmap.JmapApi
import com.mobilemail.data.local.dao.FolderDao
import com.mobilemail.data.local.dao.MessageDao
import com.mobilemail.data.model.Folder
import com.mobilemail.data.model.MessageDetail
import com.mobilemail.data.model.MessageListItem
import com.mobilemail.domain.model.Account as DomainAccount
import com.mobilemail.data.repository.Mappers.toDomainAccount
import com.mobilemail.data.repository.Mappers.toFolder
import com.mobilemail.data.repository.Mappers.toFolderEntity
import com.mobilemail.data.repository.Mappers.toMessageDetail
import com.mobilemail.data.repository.Mappers.toMessageEntity
import com.mobilemail.data.repository.Mappers.toMessageListItem
import com.mobilemail.data.repository.Mappers.toMessageListItem as entityToMessageListItem
import com.mobilemail.data.repository.AttachmentParser.parseAttachments
import com.mobilemail.domain.model.toDomain
import com.mobilemail.domain.repository.IMailRepository
import com.mobilemail.domain.model.Folder as DomainFolder
import com.mobilemail.domain.model.MessageDetail as DomainMessageDetail
import com.mobilemail.domain.model.MessageListItem as DomainMessageListItem

data class MessagePage(
    val items: List<DomainMessageListItem>,
    val nextPosition: Int?,
    val hasMore: Boolean,
    val queryState: String? = null,
    val fromCache: Boolean = false
)

class MailRepository(
    private val jmapClient: JmapApi,
    private val messageDao: MessageDao? = null,
    private val folderDao: FolderDao? = null
) : IMailRepository {
    private val client = jmapClient
    private val messageCache = mutableMapOf<String, MessageDetail>()
    private val cacheTtlMillis = 2 * 60 * 1000L


    private suspend fun getFoldersData(): Result<List<Folder>> = runCatchingSuspend {
        Log.d("MailRepository", "Начало загрузки папок")
        val session = client.getSession()
        Log.d("MailRepository", "Сессия получена для getFolders")
        val accountId = session.primaryAccounts?.mail
            ?: session.accounts.keys.firstOrNull()

        if (accountId == null) {
            Log.e("MailRepository", "AccountId не найден в сессии")
            error("AccountId не найден")
        }

        Log.d("MailRepository", "Запрос папок для accountId: $accountId")
        val mailboxes = client.getMailboxes(accountId)
        Log.d("MailRepository", "Получено папок: ${mailboxes.size}")

        mailboxes.map { it.toFolder() }
    }

    suspend fun getMessagesPage(
        folderId: String,
        position: Int = 0,
        limit: Int = 50,
        forceRefresh: Boolean = false
    ): Result<MessagePage> = runCatchingSuspend {
        Log.d("MailRepository", "Загрузка писем для папки: $folderId")
        val session = client.getSession()
        val accountId = session.primaryAccounts?.mail
            ?: session.accounts.keys.firstOrNull()

        if (accountId == null) {
            error("AccountId не найден")
        }

        val cachedMessages = messageDao?.getMessagesByFolderPaged(folderId, accountId, limit, position)
        val folderMetadata = folderDao?.getFolderById(folderId, accountId)
        val latestCachedSync = messageDao?.getLatestSyncedAtByFolder(folderId, accountId)
        val isCacheFresh = latestCachedSync != null && (System.currentTimeMillis() - latestCachedSync) <= cacheTtlMillis

        try {
            val queryResult = fetchEmailQuery(folderId, accountId, position, limit)

            if (queryResult.ids.isEmpty()) {
                return@runCatchingSuspend buildEmptyPageResult(cachedMessages, queryResult.queryState)
            }

            val cachedQueryState = folderMetadata?.queryState
            if (shouldReturnCache(forceRefresh, position, cachedMessages, cachedQueryState, queryResult.queryState, isCacheFresh)) {
                return@runCatchingSuspend MessagePage(
                    items = cachedMessages!!.map { it.toMessageListItem().toDomain() },
                    nextPosition = cachedMessages.size.takeIf { queryResult.ids.size >= limit },
                    hasMore = queryResult.ids.size >= limit,
                    queryState = queryResult.queryState,
                    fromCache = true
                )
            }

            val emails = fetchEmailDetails(folderId, accountId, queryResult.ids)
            cacheEmailDetails(emails)
            val result = mapEmailsToListItems(emails)
            persistEmailsToRoom(result, folderId, accountId, queryResult.queryState)

            Log.d("MailRepository", "Обработано писем: ${result.size}, закэшировано: ${messageCache.size}")
            MessagePage(
                items = result.map { it.toDomain() },
                nextPosition = (position + result.size).takeIf { result.size >= limit },
                hasMore = result.size >= limit,
                queryState = queryResult.queryState,
                fromCache = false
            )
        } catch (e: Exception) {
            Log.e("MailRepository", "Ошибка загрузки писем", e)
            if (cachedMessages != null && cachedMessages.isNotEmpty()) {
                Log.d("MailRepository", "Возвращаем кэшированные письма из-за ошибки сети")
                return@runCatchingSuspend MessagePage(
                    items = cachedMessages.map { it.toMessageListItem().toDomain() },
                    nextPosition = (position + cachedMessages.size).takeIf { cachedMessages.size >= limit },
                    hasMore = cachedMessages.size >= limit,
                    queryState = folderMetadata?.queryState,
                    fromCache = true
                )
            }
            throw e
        }
    }.onError { e ->
        Log.e("MailRepository", "Ошибка загрузки писем (onError)", e)
    }

    private suspend fun fetchEmailQuery(
        folderId: String,
        accountId: String,
        position: Int,
        limit: Int
    ) = run {
        Log.d("MailRepository", "Запрос писем для accountId: $accountId, mailboxId: $folderId, position: $position, limit: $limit")
        client.queryEmails(
            mailboxId = folderId,
            accountId = accountId,
            position = position,
            limit = limit
        )
    }

    private fun buildEmptyPageResult(
        cachedMessages: List<com.mobilemail.data.local.entity.MessageEntity>?,
        queryState: String?
    ): MessagePage {
        Log.d("MailRepository", "Список писем пуст")
        cachedMessages?.let {
            return MessagePage(
                items = it.map { entity -> entity.toMessageListItem().toDomain() },
                nextPosition = null,
                hasMore = false,
                queryState = queryState,
                fromCache = true
            )
        }
        return MessagePage(emptyList(), null, false, queryState)
    }

    private fun shouldReturnCache(
        forceRefresh: Boolean,
        position: Int,
        cachedMessages: List<com.mobilemail.data.local.entity.MessageEntity>?,
        cachedQueryState: String?,
        serverQueryState: String?,
        isCacheFresh: Boolean
    ): Boolean {
        val cacheIsValid = !forceRefresh && position == 0 && cachedMessages != null && cachedMessages.isNotEmpty()
        val queryStateMatches = cachedQueryState == serverQueryState
        return cacheIsValid && queryStateMatches && isCacheFresh
    }

    private suspend fun fetchEmailDetails(@Suppress("UNUSED_PARAMETER") folderId: String, accountId: String, ids: List<String>) = run {
        Log.d("MailRepository", "Загрузка полных данных для всех писем")
        client.getEmails(
            ids = ids,
            accountId = accountId,
            properties = listOf(
                "id", "threadId", "mailboxIds", "from", "to", "cc", "bcc", "subject",
                "receivedAt", "preview", "hasAttachment",
                "size", "keywords", "bodyStructure", "bodyValues", "textBody", "htmlBody"
            )
        ).also { Log.d("MailRepository", "Получено писем для обработки: ${it.size}") }
    }

    private fun cacheEmailDetails(emails: List<com.mobilemail.data.model.JmapEmail>) {
        emails.forEach { email ->
            val messageDetail = convertEmailToMessageDetail(email)
            if (messageDetail != null) {
                messageCache[email.id] = messageDetail
            }
        }
    }

    private fun mapEmailsToListItems(emails: List<com.mobilemail.data.model.JmapEmail>): List<MessageListItem> {
        return emails.map { email ->
            val item = email.toMessageListItem(::parseAttachments)
            val cachedIsUnread = messageCache[email.id]?.flags?.unread
            if (cachedIsUnread != null) {
                item.copy(flags = item.flags.copy(unread = cachedIsUnread))
            } else {
                item
            }
        }
    }

    private suspend fun persistEmailsToRoom(
        result: List<MessageListItem>,
        folderId: String,
        accountId: String,
        queryState: String?
    ) {
        messageDao?.let { dao ->
            try {
                val now = System.currentTimeMillis()
                val messagesToSave = mutableListOf<com.mobilemail.data.local.entity.MessageEntity>()
                for (messageListItem in result) {
                    val existingMessage = dao.getMessageById(messageListItem.id)
                    val finalIsUnread = existingMessage?.isUnread ?: messageListItem.flags.unread
                    val entity = messageListItem.toMessageEntity(folderId, accountId).copy(
                        isUnread = finalIsUnread,
                        syncedAt = now
                    )
                    messagesToSave.add(entity)
                }
                dao.insertMessages(messagesToSave)
                trimFolderCache(folderId, accountId)
                folderDao?.updateFolderSyncState(folderId, accountId, queryState, now)
                Log.d("MailRepository", "Письма сохранены в кэш: ${result.size}")
            } catch (e: Exception) {
                Log.e("MailRepository", "Ошибка сохранения писем в кэш", e)
            }
        }
    }

    private fun convertEmailToMessageDetail(email: com.mobilemail.data.model.JmapEmail): MessageDetail? {
        return try {
            email.toMessageDetail(::parseAttachments)
        } catch (e: Exception) {
            Log.e("MailRepository", "Ошибка конвертации письма ${email.id}", e)
            null
        }
    }

    private suspend fun getMessageData(messageId: String): Result<MessageDetail> = runCatchingSuspend {
        Log.d("MailRepository", "Загрузка письма: messageId=$messageId")

        messageCache[messageId]?.let {
            Log.d("MailRepository", "Письмо найдено в памяти кэше")
            return@runCatchingSuspend it
        }

        val cachedEntity = messageDao?.getMessageById(messageId)
        val cachedReadStatus = cachedEntity?.isUnread
        Log.d("MailRepository", "Письмо не найдено в памяти кэше, проверяем Room. Статус из Room: $cachedReadStatus")

        val accountId = resolveAccountId()
        val email = fetchSingleEmail(messageId, accountId)
        val messageDetail = buildMessageDetail(email, cachedReadStatus)

        messageCache[email.id] = messageDetail
        messageDetail
    }.onError { e ->
        Log.e("MailRepository", "Ошибка загрузки письма messageId=$messageId", e)
    }

    private suspend fun resolveAccountId(): String {
        val session = client.getSession()
        val accountId = session.primaryAccounts?.mail ?: session.accounts.keys.firstOrNull()
        Log.d("MailRepository", "AccountId для загрузки письма: $accountId")
        return accountId ?: error("AccountId не найден")
    }

    private suspend fun fetchSingleEmail(messageId: String, accountId: String): com.mobilemail.data.model.JmapEmail {
        Log.d("MailRepository", "Запрос Email/get для messageId: $messageId")
        val emails = client.getEmails(
            ids = listOf(messageId),
            accountId = accountId,
            properties = listOf(
                "id", "threadId", "mailboxIds", "from", "to", "cc", "bcc",
                "subject", "receivedAt", "bodyStructure",
                "bodyValues", "textBody", "htmlBody",
                "keywords", "size", "hasAttachment"
            )
        )
        Log.d("MailRepository", "Получено писем в ответе: ${emails.size}")
        if (emails.isEmpty()) error("Письмо не найдено в ответе")
        return emails[0]
    }

    private fun buildMessageDetail(
        email: com.mobilemail.data.model.JmapEmail,
        cachedReadStatus: Boolean?
    ): MessageDetail {
        val from = email.from?.firstOrNull()?.email
        Log.d("MailRepository", "Парсинг письма: from=$from, to=${email.to?.size ?: 0}, cc=${email.cc?.size ?: 0}")
        if (cachedReadStatus != null) {
            Log.d("MailRepository", "Используем статус прочитанности из Room: $cachedReadStatus")
        } else {
            Log.d("MailRepository", "Используем статус прочитанности с сервера")
        }
        return email.toMessageDetail(::parseAttachments, cachedReadStatus)
    }

    override suspend fun updateMessageReadStatus(messageId: String, isUnread: Boolean) {
        Log.d("MailRepository", "Обновление статуса прочитанности в кэше: messageId=$messageId, isUnread=$isUnread")

        messageCache[messageId]?.let { message ->
            val updatedMessage = message.copy(
                flags = message.flags.copy(unread = isUnread)
            )
            messageCache[messageId] = updatedMessage
            Log.d("MailRepository", "Статус обновлен в памяти кэше")
        }

        messageDao?.let { dao ->
            try {
                dao.updateReadStatus(messageId, isUnread)
                Log.d("MailRepository", "Статус обновлен в Room")
            } catch (e: Exception) {
                Log.e("MailRepository", "Ошибка обновления статуса в Room", e)
            }
        }
    }

    private suspend fun trimFolderCache(folderId: String, accountId: String, maxMessages: Int = 200) {
        val dao = messageDao ?: return
        val totalCount = dao.getMessageCountByFolder(folderId, accountId)
        if (totalCount <= maxMessages) return
        val messagesToKeep = dao.getMessagesByFolderPaged(folderId, accountId, maxMessages, 0).map { it.id }.toSet()
        val allMessages = dao.getMessagesByFolderPaged(folderId, accountId, totalCount, 0)
        allMessages.filterNot { it.id in messagesToKeep }.forEach { dao.deleteMessage(it) }
    }

    // IMailRepository

    override suspend fun getAccount(): Result<DomainAccount> = runCatchingSuspend {
        Log.d("MailRepository", "Получение аккаунта...")
        val session = client.getSession()
        Log.d("MailRepository", "Сессия получена, аккаунтов: ${session.accounts.size}")
        val accountId = session.primaryAccounts?.mail ?: session.accounts.keys.firstOrNull()
        Log.d("MailRepository", "Выбранный accountId: $accountId")
        accountId?.let { id ->
            val account = session.accounts[id]
            account?.let {
                Log.d("MailRepository", "Аккаунт найден: ${it.id}, имя: ${it.name}")
                it.toDomainAccount(id)
            } ?: error("AccountId не найден в сессии")
        } ?: error("AccountId не найден в сессии")
    }.onError { e ->
        Log.e("MailRepository", "Ошибка получения аккаунта", e)
    }

    override suspend fun getFolders(): Result<List<DomainFolder>> =
        getFoldersData().map { list -> list.map { it.toDomain() } }

    override suspend fun getMessages(folderId: String, position: Int, limit: Int): Result<List<DomainMessageListItem>> =
        getMessagesPage(folderId, position, limit).map { it.items }

    override suspend fun getMessage(messageId: String): Result<DomainMessageDetail> =
        getMessageData(messageId).map { it.toDomain() }
}
