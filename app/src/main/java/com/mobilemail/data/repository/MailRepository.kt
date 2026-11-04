package com.mobilemail.data.repository

import android.util.Log
import com.mobilemail.domain.common.Result
import com.mobilemail.domain.common.fold
import com.mobilemail.domain.common.runCatchingSuspend
import com.mobilemail.data.jmap.JmapApi
import com.mobilemail.data.local.dao.FolderDao
import com.mobilemail.data.local.dao.MessageDao
import com.mobilemail.data.local.dao.PendingOperationDao
import com.mobilemail.data.model.Folder
import com.mobilemail.data.model.MessageDetail
import com.mobilemail.data.model.MessageListItem
import com.mobilemail.data.sync.PendingReadStatusQueue
import com.mobilemail.domain.model.Account as DomainAccount
import com.mobilemail.data.repository.Mappers.toDomainAccount
import com.mobilemail.data.repository.Mappers.toFolder
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
    private val folderDao: FolderDao? = null,
    private val pendingOperationDao: PendingOperationDao? = null,
    bodyCacheSize: Int = DEFAULT_BODY_CACHE_SIZE,
    private val localAccountId: String? = null
) : IMailRepository {
    private val client = jmapClient

    /**
     * LRU-кэш открытых писем. Ограничен [bodyCacheSize], чтобы прокрутка и открытие
     * большого числа писем не удерживали все тела в памяти.
     */
    private val messageCache = object : LinkedHashMap<String, MessageDetail>(bodyCacheSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MessageDetail>?): Boolean =
            size > bodyCacheSize
    }
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

        val cachedMessages = localAccountId?.let {
            messageDao?.getMessagesByFolderPaged(folderId, it, limit, position)
        }
        val folderMetadata = localAccountId?.let { folderDao?.getFolderById(folderId, it) }
        val latestCachedSync = localAccountId?.let {
            messageDao?.getLatestSyncedAtByFolder(folderId, it)
        }
        val isCacheFresh = latestCachedSync != null && (System.currentTimeMillis() - latestCachedSync) <= cacheTtlMillis
        val pendingReadStatus = pendingReadStatusOverrides()

        try {
            val queryResult = fetchEmailQuery(folderId, accountId, position, limit)

            if (queryResult.ids.isEmpty()) {
                return@runCatchingSuspend handleEmptyPage(folderId, position, queryResult.queryState)
            }

            val cachedQueryState = folderMetadata?.queryState
            if (shouldReturnCache(forceRefresh, position, cachedMessages, cachedQueryState, queryResult.queryState, isCacheFresh)) {
                return@runCatchingSuspend MessagePage(
                    items = cachedMessages!!.map { it.toMessageListItem() }
                        .withReadStatusOverrides(pendingReadStatus)
                        .map { it.toDomain() },
                    nextPosition = cachedMessages.size.takeIf { queryResult.ids.size >= limit },
                    hasMore = queryResult.ids.size >= limit,
                    queryState = queryResult.queryState,
                    fromCache = true
                )
            }

            val emails = fetchEmailDetails(folderId, accountId, queryResult.ids)
            val serverMessages = emails.map { it.toMessageListItem(::parseAttachments) }
            persistEmailsToRoom(serverMessages, folderId, accountId, queryResult.queryState)
            val result = serverMessages.withReadStatusOverrides(pendingReadStatus)

            Log.d("MailRepository", "Обработано писем: ${result.size}")
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
                    items = cachedMessages.map { it.toMessageListItem() }
                        .withReadStatusOverrides(pendingReadStatus)
                        .map { it.toDomain() },
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

    /**
     * Обрабатывает успешный ответ Email/query с пустым списком идентификаторов.
     *
     * Пустая первая страница означает, что папка пуста: сохранённые письма этой
     * папки удаляются как устаревшие. Пустая следующая страница только завершает
     * пагинацию и не трогает уже загруженные страницы.
     */
    private suspend fun handleEmptyPage(
        folderId: String,
        position: Int,
        queryState: String?
    ): MessagePage {
        Log.d("MailRepository", "Список писем пуст: folderId=$folderId, position=$position")
        if (position == 0) {
            try {
                localAccountId?.let { messageDao?.deleteMessagesByFolder(folderId, it) }
                Log.d("MailRepository", "Кэш папки очищен: folderId=$folderId")
            } catch (e: Exception) {
                Log.e("MailRepository", "Ошибка очистки кэша папки", e)
            }
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
        Log.d("MailRepository", "Загрузка полей списка для писем")
        client.getEmails(
            ids = ids,
            accountId = accountId,
            properties = listOf(
                "id", "threadId", "from", "subject", "receivedAt",
                "preview", "hasAttachment", "size", "keywords"
            )
        ).also { Log.d("MailRepository", "Получено писем для обработки: ${it.size}") }
    }

    /**
     * Applies the unread flag requested by unfinished local operations.
     *
     * Server data stays authoritative for every other message; a local intent only masks it
     * while the offline queue can still deliver or reject it.
     */
    private fun List<MessageListItem>.withReadStatusOverrides(
        pendingReadStatus: Map<String, Boolean>
    ): List<MessageListItem> = map { item ->
        pendingReadStatus[item.id]?.let { unread ->
            item.copy(flags = item.flags.copy(unread = unread))
        } ?: item
    }

    private suspend fun pendingReadStatusOverrides(): Map<String, Boolean> =
        try {
            val operations = pendingOperationDao?.getActiveReadStatusOperations().orEmpty().filter {
                com.mobilemail.data.local.LocalAccountIdentity.of(it.server, it.email) == localAccountId
            }
            PendingReadStatusQueue.unreadOverrides(operations)
        } catch (e: Exception) {
            Log.e("MailRepository", "Не удалось прочитать ожидающие операции очереди", e)
            emptyMap()
        }

    private suspend fun persistEmailsToRoom(
        result: List<MessageListItem>,
        folderId: String,
        accountId: String,
        queryState: String?
    ) {
        val accountKey = localAccountId ?: return
        messageDao?.let { dao ->
            try {
                val now = System.currentTimeMillis()
                val messagesToSave = mutableListOf<com.mobilemail.data.local.entity.MessageEntity>()
                for (messageListItem in result) {
                    // The cache mirrors the server. Local intents are applied when reading,
                    // so a stale row must never win over a fresh server response.
                    val entity = messageListItem.toMessageEntity(folderId, accountId, accountKey).copy(syncedAt = now)
                    messagesToSave.add(entity)
                }
                dao.insertMessages(messagesToSave)
                trimFolderCache(folderId, accountKey)
                folderDao?.updateFolderSyncState(folderId, accountKey, queryState, now)
                Log.d("MailRepository", "Письма сохранены в кэш: ${result.size}")
            } catch (e: Exception) {
                Log.e("MailRepository", "Ошибка сохранения писем в кэш", e)
            }
        }
    }

    private fun cachedMessage(messageId: String): MessageDetail? =
        synchronized(messageCache) { messageCache[messageId] }

    private fun cacheMessage(message: MessageDetail) {
        synchronized(messageCache) { messageCache[message.id] = message }
    }

    private suspend fun getMessageData(messageId: String): Result<MessageDetail> {
        val pendingUnread = pendingReadStatusOverrides()[messageId]
        val fetched = runCatchingSuspend {
            Log.d("MailRepository", "Загрузка письма: messageId=$messageId")
            val accountId = resolveAccountId()
            val email = fetchSingleEmail(messageId, accountId)
            buildMessageDetail(email, pendingUnread).also { cacheMessage(it) }
        }.onError { e ->
            Log.e("MailRepository", "Ошибка загрузки письма messageId=$messageId", e)
        }

        return fetched.fold(
            onSuccess = { Result.Success(it) },
            onError = { error ->
                // Offline fallback: the cached copy is the last server state plus any local intent.
                val cachedMessage = cachedMessage(messageId)
                    ?: localAccountId?.let { messageDao?.getMessageById(messageId, it)?.toMessageDetail() }
                cachedMessage?.let { Result.Success(it.withUnreadOverride(pendingUnread)) }
                    ?: Result.Error(error)
            }
        )
    }

    private fun MessageDetail.withUnreadOverride(unread: Boolean?): MessageDetail =
        unread?.let { copy(flags = flags.copy(unread = it)) } ?: this

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
        pendingUnread: Boolean?
    ): MessageDetail {
        val from = email.from?.firstOrNull()?.email
        Log.d("MailRepository", "Парсинг письма: from=$from, to=${email.to?.size ?: 0}, cc=${email.cc?.size ?: 0}")
        if (pendingUnread != null) {
            Log.d("MailRepository", "Переопределяем прочитанность ожидающим локальным действием: $pendingUnread")
        } else {
            Log.d("MailRepository", "Используем статус прочитанности с сервера")
        }
        return email.toMessageDetail(::parseAttachments, pendingUnread)
    }

    /**
     * Writes a read status that the server already accepted, or that must be restored after
     * a rejected local action, into the caches.
     */
    override suspend fun updateMessageReadStatus(messageId: String, isUnread: Boolean) {
        Log.d("MailRepository", "Обновление статуса прочитанности в кэше: messageId=$messageId, isUnread=$isUnread")

        cachedMessage(messageId)?.let { message ->
            cacheMessage(
                message.copy(flags = message.flags.copy(unread = isUnread))
            )
            Log.d("MailRepository", "Статус обновлен в памяти кэше")
        }

        localAccountId?.let { accountKey ->
            try {
                messageDao?.updateReadStatus(messageId, accountKey, isUnread)
                Log.d("MailRepository", "Статус обновлен в Room")
            } catch (e: Exception) {
                Log.e("MailRepository", "Ошибка обновления статуса в Room", e)
            }
        }
    }

    /**
     * Whether an unfinished local read-status operation currently overrides the server value.
     */
    suspend fun hasPendingReadStatusChange(messageId: String): Boolean =
        pendingReadStatusOverrides().containsKey(messageId)

    private suspend fun trimFolderCache(folderId: String, localAccountId: String, maxMessages: Int = 200) {
        val dao = messageDao ?: return
        val totalCount = dao.getMessageCountByFolder(folderId, localAccountId)
        if (totalCount <= maxMessages) return
        val messagesToKeep = dao.getMessagesByFolderPaged(folderId, localAccountId, maxMessages, 0).map { it.id }.toSet()
        val allMessages = dao.getMessagesByFolderPaged(folderId, localAccountId, totalCount, 0)
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

    private companion object {
        const val DEFAULT_BODY_CACHE_SIZE = 16
    }
}
