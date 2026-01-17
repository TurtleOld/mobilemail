package com.mobilemail.data.repository

import android.util.Log
import com.mobilemail.data.jmap.JmapClient
import com.mobilemail.data.model.*
import java.time.Instant
import java.util.Date

class MailRepository(
    private val jmapClient: JmapClient
) {
    // Кэш полных данных писем для переиспользования
    private val messageCache = mutableMapOf<String, MessageDetail>()
    suspend fun getAccount(): Account? {
        return try {
            Log.d("MailRepository", "Получение аккаунта...")
            val session = jmapClient.getSession()
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
                }
            } ?: run {
                Log.w("MailRepository", "AccountId не найден в сессии")
                null
            }
        } catch (e: Exception) {
            Log.e("MailRepository", "Ошибка получения аккаунта", e)
            null
        }
    }

    suspend fun getFolders(): List<Folder> {
        return try {
            val session = jmapClient.getSession()
            val accountId = session.primaryAccounts?.mail 
                ?: session.accounts.keys.firstOrNull()
            
            if (accountId == null) return emptyList()
            
            val mailboxes = jmapClient.getMailboxes(accountId)
            
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
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getMessages(folderId: String, limit: Int = 50): List<MessageListItem> {
        return try {
            Log.d("MailRepository", "Загрузка писем для папки: $folderId")
            val session = jmapClient.getSession()
            val accountId = session.primaryAccounts?.mail 
                ?: session.accounts.keys.firstOrNull()
            
            if (accountId == null) {
                Log.w("MailRepository", "AccountId не найден")
                return emptyList()
            }
            
            Log.d("MailRepository", "Запрос писем для accountId: $accountId, mailboxId: $folderId")
            val queryResult = jmapClient.queryEmails(
                mailboxId = folderId,
                accountId = accountId,
                position = 0,
                limit = limit
            )
            
            Log.d("MailRepository", "Найдено писем: ${queryResult.ids.size}")
            if (queryResult.ids.isEmpty()) {
                Log.d("MailRepository", "Список писем пуст")
                return emptyList()
            }
            
            // Загружаем полные данные для всех писем сразу (включая тело)
            Log.d("MailRepository", "Загрузка полных данных для всех писем")
            val emails = jmapClient.getEmails(
                ids = queryResult.ids,
                accountId = accountId,
                properties = listOf(
                    "id", "threadId", "mailboxIds", "from", "to", "cc", "bcc", "subject",
                    "receivedAt", "preview", "hasAttachment",
                    "size", "keywords", "bodyStructure", "bodyValues", "textBody", "htmlBody"
                )
            )
            
            Log.d("MailRepository", "Получено писем для обработки: ${emails.size}")
            
            // Кэшируем полные данные писем
            emails.forEach { email ->
                val messageDetail = convertEmailToMessageDetail(email)
                if (messageDetail != null) {
                    messageCache[email.id] = messageDetail
                }
            }
            
            val result = emails.map { email ->
                val from = email.from?.firstOrNull() 
                    ?: EmailAddress(email = "unknown")
                val isUnread = email.keywords?.get("\$seen") != true
                val isStarred = email.keywords?.get("\$flagged") == true
                val isImportant = email.keywords?.get("\$important") == true
                
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
                        hasAttachments = email.hasAttachment == true
                    ),
                    size = email.size
                )
            }
            Log.d("MailRepository", "Обработано писем: ${result.size}, закэшировано: ${messageCache.size}")
            result
        } catch (e: Exception) {
            Log.e("MailRepository", "Ошибка загрузки писем", e)
            emptyList()
        }
    }
    
    private fun convertEmailToMessageDetail(email: com.mobilemail.data.model.JmapEmail): MessageDetail? {
        return try {
            // Обрабатываем строку "null" как null
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
            
            // Извлекаем тело письма
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
            
            // Если не нашли по partId, берем первое доступное значение
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
                attachments = emptyList(),
                flags = MessageFlags(
                    unread = isUnread,
                    starred = isStarred,
                    important = isImportant,
                    hasAttachments = email.hasAttachment == true
                )
            )
        } catch (e: Exception) {
            android.util.Log.e("MailRepository", "Ошибка конвертации письма ${email.id}", e)
            null
        }
    }

    suspend fun getMessage(messageId: String): MessageDetail? {
        return try {
            android.util.Log.d("MailRepository", "Загрузка письма: messageId=$messageId")
            
            // Сначала проверяем кэш
            messageCache[messageId]?.let {
                android.util.Log.d("MailRepository", "Письмо найдено в кэше")
                return it
            }
            
            android.util.Log.d("MailRepository", "Письмо не найдено в кэше, загружаем с сервера")
            val session = jmapClient.getSession()
            val accountId = session.primaryAccounts?.mail 
                ?: session.accounts.keys.firstOrNull()
            
            android.util.Log.d("MailRepository", "AccountId для загрузки письма: $accountId")
            if (accountId == null) {
                android.util.Log.w("MailRepository", "AccountId не найден")
                return null
            }
            
            android.util.Log.d("MailRepository", "Запрос Email/get для messageId: $messageId")
            val emails = jmapClient.getEmails(
                ids = listOf(messageId),
                accountId = accountId,
                properties = listOf(
                    "id", "threadId", "mailboxIds", "from", "to", "cc", "bcc",
                    "subject", "receivedAt", "bodyStructure",
                    "bodyValues", "textBody", "htmlBody",
                    "keywords", "size", "hasAttachment"
                )
            )
            
            android.util.Log.d("MailRepository", "Получено писем в ответе: ${emails.size}")
            if (emails.isEmpty()) {
                android.util.Log.w("MailRepository", "Письмо не найдено в ответе")
                return null
            }
            
            val email = emails[0]
            // Обрабатываем строку "null" как null
            val fromEmail = email.from?.firstOrNull()
            val from = if (fromEmail != null) {
                val name = if (fromEmail.name == null || fromEmail.name == "null" || fromEmail.name.isBlank()) null else fromEmail.name
                val emailAddr = if (fromEmail.email.isBlank() || fromEmail.email == "null") "unknown" else fromEmail.email
                EmailAddress(name, emailAddr)
            } else {
                EmailAddress(email = "unknown")
            }
            
            android.util.Log.d("MailRepository", "Парсинг письма: from=${from.email}, to=${email.to?.size ?: 0}, cc=${email.cc?.size ?: 0}")
            android.util.Log.d("MailRepository", "bodyValues keys: ${email.bodyValues?.keys?.joinToString() ?: "null"}")
            android.util.Log.d("MailRepository", "textBody parts: ${email.textBody?.size ?: 0}, htmlBody parts: ${email.htmlBody?.size ?: 0}")
            
            var textBody: String? = null
            var htmlBody: String? = null
            
            // Сначала пытаемся найти тело по partId из textBody/htmlBody
            email.textBody?.forEach { textPart ->
                android.util.Log.d("MailRepository", "Проверка textBody partId: ${textPart.partId}")
                email.bodyValues?.get(textPart.partId)?.let {
                    textBody = it.value
                    android.util.Log.d("MailRepository", "Найден textBody для partId: ${textPart.partId}, длина: ${it.value.length}")
                } ?: run {
                    android.util.Log.w("MailRepository", "Не найден bodyValue для textBody partId: ${textPart.partId}")
                }
            }
            
            email.htmlBody?.forEach { htmlPart ->
                android.util.Log.d("MailRepository", "Проверка htmlBody partId: ${htmlPart.partId}")
                email.bodyValues?.get(htmlPart.partId)?.let {
                    htmlBody = it.value
                    android.util.Log.d("MailRepository", "Найден htmlBody для partId: ${htmlPart.partId}, длина: ${it.value.length}")
                } ?: run {
                    android.util.Log.w("MailRepository", "Не найден bodyValue для htmlBody partId: ${htmlPart.partId}")
                }
            }
            
            // Если не нашли по partId, берем первое доступное значение
            if (textBody == null && htmlBody == null) {
                android.util.Log.d("MailRepository", "Не найдено тело по partId, ищем первое доступное значение")
                email.bodyValues?.values?.firstOrNull()?.let {
                    val content = it.value.trim()
                    val looksLikeHtml = content.startsWith("<") && 
                        (content.contains("<html") || content.contains("<body") || 
                         content.contains("<div") || content.contains("<p"))
                    
                    if (looksLikeHtml) {
                        htmlBody = it.value
                        android.util.Log.d("MailRepository", "Найден htmlBody как первое значение, длина: ${it.value.length}")
                    } else {
                        textBody = it.value
                        android.util.Log.d("MailRepository", "Найден textBody как первое значение, длина: ${it.value.length}")
                    }
                } ?: run {
                    android.util.Log.w("MailRepository", "bodyValues пуст или null")
                }
            }
            
            android.util.Log.d("MailRepository", "Итоговое тело: textBody=${textBody != null}, htmlBody=${htmlBody != null}")
            
            val isUnread = email.keywords?.get("\$seen") != true
            val isStarred = email.keywords?.get("\$flagged") == true
            val isImportant = email.keywords?.get("\$important") == true
            
            android.util.Log.d("MailRepository", "Исходные данные email.to: ${email.to?.size ?: 0} адресов")
            email.to?.forEachIndexed { index, addr ->
                android.util.Log.d("MailRepository", "  Исходный адрес [$index]: name='${addr.name}', email='${addr.email}'")
            }
            
            val toList = email.to?.map { 
                // Обрабатываем строку "null" как null
                val name = if (it.name == null || it.name == "null" || it.name.isBlank()) null else it.name
                val emailAddr = if (it.email.isBlank() || it.email == "null") "unknown" else it.email
                EmailAddress(name, emailAddr)
            } ?: emptyList()
            android.util.Log.d("MailRepository", "Создание MessageDetail: to.size=${toList.size}, to=${toList.map { it.email }.joinToString()}")
            
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
                attachments = emptyList(),
                flags = MessageFlags(
                    unread = isUnread,
                    starred = isStarred,
                    important = isImportant,
                    hasAttachments = email.hasAttachment == true
                )
            )
            
            // Сохраняем в кэш
            messageCache[email.id] = messageDetail
            messageDetail
        } catch (e: Exception) {
            android.util.Log.e("MailRepository", "Ошибка загрузки письма messageId=$messageId", e)
            null
        }
    }
}
