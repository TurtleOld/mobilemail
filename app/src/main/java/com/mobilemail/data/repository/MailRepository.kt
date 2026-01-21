package com.mobilemail.data.repository

import android.util.Log
import com.mobilemail.data.common.Result
import com.mobilemail.data.common.runCatchingSuspend
import com.mobilemail.data.jmap.JmapClient
import com.mobilemail.data.local.dao.FolderDao
import com.mobilemail.data.local.dao.MessageDao
import com.mobilemail.data.model.Account
import com.mobilemail.data.model.EmailAddress
import com.mobilemail.data.model.Folder
import com.mobilemail.data.model.FolderRole
import com.mobilemail.data.model.MessageBody
import com.mobilemail.data.model.MessageDetail
import com.mobilemail.data.model.MessageFlags
import com.mobilemail.data.model.MessageListItem
import com.mobilemail.data.repository.Mappers.toFolderEntity
import com.mobilemail.data.repository.Mappers.toMessageEntity
import com.mobilemail.data.repository.Mappers.toMessageListItem
import com.mobilemail.data.repository.Mappers.toMessageListItem as entityToMessageListItem
import com.mobilemail.data.repository.AttachmentParser.parseAttachments
import java.time.Instant
import java.util.Date

class MailRepository(
    private val jmapClient: Any,
    private val messageDao: MessageDao? = null,
    private val folderDao: FolderDao? = null
) {
    @Suppress("UNCHECKED_CAST")
    private val client = jmapClient as? JmapClient ?: jmapClient as com.mobilemail.data.jmap.JmapOAuthClient
    private val messageCache = mutableMapOf<String, MessageDetail>()
    
    suspend fun getAccount(): Result<Account> = runCatchingSuspend {
        Log.d("MailRepository", "Получение аккаунта...")
        val session = client.getSession()
        Log.d("MailRepository", "Сессия получена, аккаунтов: ${session.accounts.size}")
        
        val accountId = session.primaryAccounts?.mail 
            ?: session.accounts.keys.firstOrNull()
        
        Log.d("MailRepository", "Выбранный accountId: $accountId")
        
        accountId?.let { id ->
            val account = session.accounts[id]
            account?.let {
                Log.d("MailRepository", "Аккаунт найден: ${it.id}, имя: ${it.name}")
                Account(
                    id = it.id,
                    email = it.id,
                    displayName = it.name
                )
            } ?: throw IllegalStateException("AccountId не найден в сессии")
        } ?: throw IllegalStateException("AccountId не найден в сессии")
    }.onError { e ->
        Log.e("MailRepository", "Ошибка получения аккаунта", e)
    }

    suspend fun getFolders(): Result<List<Folder>> = runCatchingSuspend {
        Log.d("MailRepository", "Начало загрузки папок")
        val session = client.getSession()
        Log.d("MailRepository", "Сессия получена для getFolders")
        val accountId = session.primaryAccounts?.mail 
            ?: session.accounts.keys.firstOrNull()
        
        if (accountId == null) {
            Log.e("MailRepository", "AccountId не найден в сессии")
            throw IllegalStateException("AccountId не найден")
        }
        
        Log.d("MailRepository", "Запрос папок для accountId: $accountId")
        val mailboxes = client.getMailboxes(accountId)
        Log.d("MailRepository", "Получено папок: ${mailboxes.size}")
        
        mailboxes.map { mailbox ->
            val role = when (mailbox.role) {
                "inbox" -> FolderRole.INBOX
                "sent" -> FolderRole.SENT
                "drafts" -> FolderRole.DRAFTS
                "trash" -> FolderRole.TRASH
                "spam", "junk" -> FolderRole.SPAM
                "archive" -> FolderRole.ARCHIVE
                else -> FolderRole.CUSTOM
            }
            
            Folder(
                id = mailbox.id,
                name = mailbox.name,
                role = role,
                unreadCount = mailbox.unreadEmails ?: 0
            )
        }
    }

    suspend fun getMessages(
        folderId: String, 
        position: Int = 0,
        limit: Int = 50
    ): Result<List<MessageListItem>> = runCatchingSuspend {
        Log.d("MailRepository", "Загрузка писем для папки: $folderId")
        val session = client.getSession()
        val accountId = session.primaryAccounts?.mail 
            ?: session.accounts.keys.firstOrNull()
        
        if (accountId == null) {
            throw IllegalStateException("AccountId не найден")
        }
        
        // Сначала пытаемся загрузить из кэша
        val cachedMessages = messageDao?.getMessagesByFolderPaged(folderId, accountId, limit, position)
        
        try {
            Log.d("MailRepository", "Запрос писем для accountId: $accountId, mailboxId: $folderId, position: $position, limit: $limit")
            val queryResult = client.queryEmails(
                mailboxId = folderId,
                accountId = accountId,
                position = position,
                limit = limit
            )
            
            Log.d("MailRepository", "Найдено писем: ${queryResult.ids.size}")
            if (queryResult.ids.isEmpty()) {
                Log.d("MailRepository", "Список писем пуст")
            // Если есть кэш, возвращаем его
            cachedMessages?.let { return@runCatchingSuspend it.map { it.toMessageListItem() } }
                return@runCatchingSuspend emptyList()
            }
        
        Log.d("MailRepository", "Загрузка полных данных для всех писем")
        val emails = client.getEmails(
            ids = queryResult.ids,
            accountId = accountId,
            properties = listOf(
                "id", "threadId", "mailboxIds", "from", "to", "cc", "bcc", "subject",
                "receivedAt", "preview", "hasAttachment",
                "size", "keywords", "bodyStructure", "bodyValues", "textBody", "htmlBody"
            )
        )
        
        Log.d("MailRepository", "Получено писем для обработки: ${emails.size}")
        
        emails.forEach { email ->
            val messageDetail = convertEmailToMessageDetail(email)
            if (messageDetail != null) {
                messageCache[email.id] = messageDetail
            }
        }
        
        val result = emails.map { email ->
            val from = email.from?.firstOrNull() 
                ?: EmailAddress(email = "unknown")
            
            // Проверяем обновленный статус в кэше, если он есть
            val cachedMessage = messageCache[email.id]
            val isUnread = if (cachedMessage != null) {
                // Используем статус из кэша, если он был обновлен локально
                cachedMessage.flags.unread
            } else {
                // Иначе используем статус с сервера
                email.keywords?.get("\$seen") != true
            }
            
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
                    val parsedAttachments = parseAttachments(bodyStructureJson)
                    parsedAttachments.isNotEmpty()
                } catch (e: Exception) {
                    Log.e("MailRepository", "Ошибка парсинга вложений для списка", e)
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
        
        // Сохраняем в кэш, но сохраняем обновленный статус прочитанности из Room
        messageDao?.let { dao ->
            try {
                val messagesToSave = result.map { messageListItem ->
                    // Проверяем, есть ли обновленный статус в Room
                    val existingMessage = dao.getMessageById(messageListItem.id)
                    val finalIsUnread = existingMessage?.isUnread ?: messageListItem.flags.unread
                    
                    // Создаем MessageEntity с сохранением обновленного статуса
                    messageListItem.toMessageEntity(folderId, accountId).copy(
                        isUnread = finalIsUnread
                    )
                }
                dao.insertMessages(messagesToSave)
                Log.d("MailRepository", "Письма сохранены в кэш: ${result.size}")
            } catch (e: Exception) {
                Log.e("MailRepository", "Ошибка сохранения писем в кэш", e)
            }
        }
        
            Log.d("MailRepository", "Обработано писем: ${result.size}, закэшировано: ${messageCache.size}")
            result
        } catch (e: Exception) {
            Log.e("MailRepository", "Ошибка загрузки писем", e)
            // При ошибке сети пытаемся вернуть кэш
            if (cachedMessages != null && cachedMessages.isNotEmpty()) {
                Log.d("MailRepository", "Возвращаем кэшированные письма из-за ошибки сети")
                return@runCatchingSuspend cachedMessages.map { it.toMessageListItem() }
            }
            throw e
        }
    }.onError { e ->
        Log.e("MailRepository", "Ошибка загрузки писем (onError)", e)
    }
    
    private fun convertEmailToMessageDetail(email: com.mobilemail.data.model.JmapEmail): MessageDetail? {
        return try {
            val fromEmail = email.from?.firstOrNull()
            val from = if (fromEmail != null) {
                val name = if (fromEmail.name == null || fromEmail.name == "null" || fromEmail.name.isBlank()) null else fromEmail.name
                val emailAddr = if (fromEmail.email.isBlank() || fromEmail.email == "null") "unknown" else fromEmail.email
                EmailAddress(name, emailAddr)
            } else {
                EmailAddress(email = "unknown")
            }
            
            var textBody: String? = null
            var htmlBody: String? = null
            
            email.textBody?.forEach { textPart ->
                email.bodyValues?.get(textPart.partId)?.let {
                    textBody = it.value
                }
            }
            
            email.htmlBody?.forEach { htmlPart ->
                email.bodyValues?.get(htmlPart.partId)?.let {
                    htmlBody = it.value
                }
            }
            
            if (textBody == null && htmlBody == null) {
                email.bodyValues?.values?.firstOrNull()?.let {
                    val content = it.value.trim()
                    val looksLikeHtml = content.startsWith("<") &&
                        (content.contains("<html") || content.contains("<body") ||
                         content.contains("<div") || content.contains("<p"))
                    
                    if (looksLikeHtml) {
                        htmlBody = it.value
                    } else {
                        textBody = it.value
                    }
                }
            }
            
            val isUnread = email.keywords?.get("\$seen") != true
            val isStarred = email.keywords?.get("\$flagged") == true
            val isImportant = email.keywords?.get("\$important") == true
            
            // Парсим вложения из bodyStructure
            val attachments = if (email.bodyStructure != null) {
                try {
                    val bodyStructureJson = when (email.bodyStructure) {
                        is org.json.JSONObject -> email.bodyStructure
                        is org.json.JSONArray -> email.bodyStructure
                        else -> null
                    }
                    if (bodyStructureJson != null) {
                        parseAttachments(bodyStructureJson)
                    } else {
                        emptyList()
                    }
                } catch (e: Exception) {
                    Log.e("MailRepository", "Ошибка парсинга вложений для списка", e)
                    emptyList()
                }
            } else {
                emptyList()
            }
            
            val toList = email.to?.map { 
                val name = if (it.name == null || it.name == "null" || it.name.isBlank()) null else it.name
                val emailAddr = if (it.email.isBlank() || it.email == "null") "unknown" else it.email
                EmailAddress(name, emailAddr)
            } ?: emptyList()
            
            MessageDetail(
                id = email.id,
                threadId = email.threadId,
                from = from,
                to = toList,
                cc = email.cc?.map { 
                    val name = if (it.name == null || it.name == "null" || it.name.isBlank()) null else it.name
                    val emailAddr = if (it.email.isBlank() || it.email == "null") "unknown" else it.email
                    EmailAddress(name, emailAddr)
                },
                bcc = email.bcc?.map { 
                    val name = if (it.name == null || it.name == "null" || it.name.isBlank()) null else it.name
                    val emailAddr = if (it.email.isBlank() || it.email == "null") "unknown" else it.email
                    EmailAddress(name, emailAddr)
                },
                subject = email.subject ?: "(без темы)",
                date = try {
                    Date.from(Instant.parse(email.receivedAt))
                } catch (e: Exception) {
                    Date()
                },
                body = MessageBody(text = textBody, html = htmlBody),
                attachments = attachments,
                flags = MessageFlags(
                    unread = isUnread,
                    starred = isStarred,
                    important = isImportant,
                    hasAttachments = email.hasAttachment == true || attachments.isNotEmpty()
                )
            )
        } catch (e: Exception) {
            Log.e("MailRepository", "Ошибка конвертации письма ${email.id}", e)
            null
        }
    }

    suspend fun getMessage(messageId: String): Result<MessageDetail> = runCatchingSuspend {
        Log.d("MailRepository", "Загрузка письма: messageId=$messageId")
        
        // Сначала проверяем in-memory кэш
        messageCache[messageId]?.let {
            Log.d("MailRepository", "Письмо найдено в памяти кэше")
            return@runCatchingSuspend it
        }
        
        // Проверяем Room для получения обновленного статуса
        val cachedEntity = messageDao?.getMessageById(messageId)
        val cachedReadStatus = cachedEntity?.isUnread
        
        Log.d("MailRepository", "Письмо не найдено в памяти кэше, проверяем Room. Статус из Room: $cachedReadStatus")
        
        val session = client.getSession()
        val accountId = session.primaryAccounts?.mail 
            ?: session.accounts.keys.firstOrNull()
        
        Log.d("MailRepository", "AccountId для загрузки письма: $accountId")
        if (accountId == null) {
            throw IllegalStateException("AccountId не найден")
        }
        
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
        if (emails.isEmpty()) {
            throw IllegalStateException("Письмо не найдено в ответе")
        }
        
        val email = emails[0]
        val fromEmail = email.from?.firstOrNull()
        val from = if (fromEmail != null) {
            val name = if (fromEmail.name == null || fromEmail.name == "null" || fromEmail.name.isBlank()) null else fromEmail.name
            val emailAddr = if (fromEmail.email.isBlank() || fromEmail.email == "null") "unknown" else fromEmail.email
            EmailAddress(name, emailAddr)
        } else {
            EmailAddress(email = "unknown")
        }
        
        Log.d("MailRepository", "Парсинг письма: from=${from.email}, to=${email.to?.size ?: 0}, cc=${email.cc?.size ?: 0}")
        
        var textBody: String? = null
        var htmlBody: String? = null
        
        email.textBody?.forEach { textPart ->
            email.bodyValues?.get(textPart.partId)?.let {
                textBody = it.value
            }
        }
        
        email.htmlBody?.forEach { htmlPart ->
            email.bodyValues?.get(htmlPart.partId)?.let {
                htmlBody = it.value
            }
        }
        
        if (textBody == null && htmlBody == null) {
            email.bodyValues?.values?.firstOrNull()?.let {
                val content = it.value.trim()
                val looksLikeHtml = content.startsWith("<") &&
                    (content.contains("<html") || content.contains("<body") ||
                     content.contains("<div") || content.contains("<p"))
                
                if (looksLikeHtml) {
                    htmlBody = it.value
                } else {
                    textBody = it.value
                }
            }
        }
        
        val isUnread = email.keywords?.get("\$seen") != true
        val isStarred = email.keywords?.get("\$flagged") == true
        val isImportant = email.keywords?.get("\$important") == true
        
        // Парсим вложения из bodyStructure
        val attachments = if (email.bodyStructure != null) {
            try {
                val bodyStructureJson = when (email.bodyStructure) {
                    is org.json.JSONObject -> email.bodyStructure
                    is org.json.JSONArray -> email.bodyStructure
                    else -> null
                }
                
                if (bodyStructureJson != null) {
                    parseAttachments(bodyStructureJson)
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                Log.e("MailRepository", "Ошибка парсинга вложений", e)
                emptyList()
            }
        } else {
            emptyList()
        }
        
        val toList = email.to?.map { 
            val name = if (it.name == null || it.name == "null" || it.name.isBlank()) null else it.name
            val emailAddr = if (it.email.isBlank() || it.email == "null") "unknown" else it.email
            EmailAddress(name, emailAddr)
        } ?: emptyList()
        
        // Используем статус из Room, если он есть (обновленный локально), иначе с сервера
        val finalIsUnread = if (cachedReadStatus != null) {
            Log.d("MailRepository", "Используем статус прочитанности из Room: $cachedReadStatus")
            cachedReadStatus
        } else {
            Log.d("MailRepository", "Используем статус прочитанности с сервера: $isUnread")
            isUnread
        }
        
        val messageDetail = MessageDetail(
            id = email.id,
            threadId = email.threadId,
            from = from,
            to = toList,
            cc = email.cc?.map { 
                val name = if (it.name == null || it.name == "null" || it.name.isBlank()) null else it.name
                val emailAddr = if (it.email.isBlank() || it.email == "null") "unknown" else it.email
                EmailAddress(name, emailAddr)
            },
            bcc = email.bcc?.map { 
                val name = if (it.name == null || it.name == "null" || it.name.isBlank()) null else it.name
                val emailAddr = if (it.email.isBlank() || it.email == "null") "unknown" else it.email
                EmailAddress(name, emailAddr)
            },
            subject = email.subject ?: "(без темы)",
            date = try {
                Date.from(Instant.parse(email.receivedAt))
            } catch (e: Exception) {
                Date()
            },
            body = MessageBody(text = textBody, html = htmlBody),
            attachments = attachments,
            flags = MessageFlags(
                unread = finalIsUnread,
                starred = isStarred,
                important = isImportant,
                hasAttachments = email.hasAttachment == true || attachments.isNotEmpty()
            )
        )
        
        messageCache[email.id] = messageDetail
        messageDetail
    }.onError { e ->
        Log.e("MailRepository", "Ошибка загрузки письма messageId=$messageId", e)
    }
    
    suspend fun updateMessageReadStatus(messageId: String, isUnread: Boolean) {
        Log.d("MailRepository", "Обновление статуса прочитанности в кэше: messageId=$messageId, isUnread=$isUnread")
        
        // Обновляем в памяти кэше
        messageCache[messageId]?.let { message ->
            val updatedMessage = message.copy(
                flags = message.flags.copy(unread = isUnread)
            )
            messageCache[messageId] = updatedMessage
            Log.d("MailRepository", "Статус обновлен в памяти кэше")
        }
        
        // Обновляем в Room
        messageDao?.let { dao ->
            try {
                dao.updateReadStatus(messageId, isUnread)
                Log.d("MailRepository", "Статус обновлен в Room")
            } catch (e: Exception) {
                Log.e("MailRepository", "Ошибка обновления статуса в Room", e)
            }
        }
    }
}
