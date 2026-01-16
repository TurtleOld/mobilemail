package com.mobilemail.data.repository

import com.mobilemail.data.jmap.JmapClient
import com.mobilemail.data.model.*
import java.time.Instant
import java.util.Date

class MailRepository(
    private val jmapClient: JmapClient
) {
    suspend fun getAccount(): Account? {
        return try {
            val session = jmapClient.getSession()
            val accountId = session.primaryAccounts?.mail 
                ?: session.accounts.keys.firstOrNull()
            
            accountId?.let { id ->
                val account = session.accounts[id]
                account?.let {
                    Account(
                        id = it.id,
                        email = it.id,
                        displayName = it.name
                    )
                }
            }
        } catch (e: Exception) {
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
                    'inbox' -> FolderRole.INBOX
                    'sent' -> FolderRole.SENT
                    'drafts' -> FolderRole.DRAFTS
                    'trash' -> FolderRole.TRASH
                    'spam', 'junk' -> FolderRole.SPAM
                    'archive' -> FolderRole.ARCHIVE
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
            val session = jmapClient.getSession()
            val accountId = session.primaryAccounts?.mail 
                ?: session.accounts.keys.firstOrNull()
            
            if (accountId == null) return emptyList()
            
            val queryResult = jmapClient.queryEmails(
                mailboxId = folderId,
                accountId = accountId,
                position = 0,
                limit = limit
            )
            
            if (queryResult.ids.isEmpty()) return emptyList()
            
            val emails = jmapClient.getEmails(
                ids = queryResult.ids,
                accountId = accountId,
                properties = listOf(
                    'id', 'threadId', 'from', 'subject',
                    'receivedAt', 'preview', 'hasAttachment',
                    'size', 'keywords'
                )
            )
            
            emails.map { email ->
                val from = email.from?.firstOrNull() 
                    ?: EmailAddress(email = 'unknown')
                val isUnread = email.keywords?.get('$seen') != true
                val isStarred = email.keywords?.get('$flagged') == true
                val isImportant = email.keywords?.get('$important') == true
                
                MessageListItem(
                    id = email.id,
                    threadId = email.threadId,
                    from = from,
                    subject = email.subject ?: '(без темы)',
                    snippet = email.preview ?: '',
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
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getMessage(messageId: String): MessageDetail? {
        return try {
            val session = jmapClient.getSession()
            val accountId = session.primaryAccounts?.mail 
                ?: session.accounts.keys.firstOrNull()
            
            if (accountId == null) return null
            
            val emails = jmapClient.getEmails(
                ids = listOf(messageId),
                accountId = accountId,
                properties = listOf(
                    'id', 'threadId', 'from', 'to', 'cc', 'bcc',
                    'subject', 'receivedAt', 'bodyStructure',
                    'bodyValues', 'textBody', 'htmlBody',
                    'keywords'
                )
            )
            
            if (emails.isEmpty()) return null
            
            val email = emails[0]
            val from = email.from?.firstOrNull() 
                ?: EmailAddress(email = 'unknown')
            
            var textBody: String? = null
            var htmlBody: String? = null
            
            email.bodyValues?.forEach { (partId, bodyValue) ->
                if (email.textBody?.any { it.partId == partId } == true) {
                    textBody = bodyValue.value
                }
                if (email.htmlBody?.any { it.partId == partId } == true) {
                    htmlBody = bodyValue.value
                }
            }
            
            if (textBody == null && htmlBody == null) {
                email.bodyValues?.values?.firstOrNull()?.let {
                    val content = it.value.trim()
                    val looksLikeHtml = content.startsWith('<') && 
                        (content.contains('<html') || content.contains('<body') || 
                         content.contains('<div') || content.contains('<p'))
                    
                    if (looksLikeHtml) {
                        htmlBody = it.value
                    } else {
                        textBody = it.value
                    }
                }
            }
            
            val isUnread = email.keywords?.get('$seen') != true
            val isStarred = email.keywords?.get('$flagged') == true
            val isImportant = email.keywords?.get('$important') == true
            
            MessageDetail(
                id = email.id,
                threadId = email.threadId,
                from = from,
                to = email.to?.map { EmailAddress(it.name, it.email) } ?: emptyList(),
                cc = email.cc?.map { EmailAddress(it.name, it.email) },
                bcc = email.bcc?.map { EmailAddress(it.name, it.email) },
                subject = email.subject ?: '(без темы)',
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
            null
        }
    }
}
