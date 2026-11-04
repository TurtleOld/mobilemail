package com.mobilemail.data.repository

import com.mobilemail.data.jmap.JmapApi
import com.mobilemail.data.local.dao.MessageDao
import com.mobilemail.data.local.entity.MessageEntity
import com.mobilemail.data.model.Attachment
import com.mobilemail.data.model.EmailQueryResult
import com.mobilemail.data.model.EmailSubmissionStatus
import com.mobilemail.data.model.JmapAccount
import com.mobilemail.data.model.JmapEmail
import com.mobilemail.data.model.JmapMailbox
import com.mobilemail.data.model.JmapSession
import com.mobilemail.data.model.PrimaryAccounts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.Date

class MailRepositoryTest {

    @Test
    fun `empty first page clears cached folder`() = runTest {
        val dao = InMemoryMessageDao(listOf(cachedMessage("m1"), cachedMessage("m2")))
        val repository = MailRepository(FakeJmapApi { position -> emptyQuery(position) }, dao)

        val page = repository.getMessagesPage(FOLDER_ID, position = 0, limit = PAGE_SIZE, forceRefresh = true).getOrThrow()

        assertTrue(page.items.isEmpty())
        assertFalse(page.hasMore)
        assertFalse(page.fromCache)
        assertTrue(dao.messages.isEmpty())
    }

    @Test
    fun `empty next page ends pagination and keeps cached pages`() = runTest {
        val cached = listOf(cachedMessage("m1"), cachedMessage("m2"), cachedMessage("m3"))
        val dao = InMemoryMessageDao(cached)
        val repository = MailRepository(FakeJmapApi { position -> emptyQuery(position) }, dao)

        val page = repository.getMessagesPage(FOLDER_ID, position = 2, limit = PAGE_SIZE).getOrThrow()

        assertTrue(page.items.isEmpty())
        assertFalse(page.hasMore)
        assertNull(page.nextPosition)
        assertEquals(cached, dao.messages)
    }

    @Test
    fun `network error keeps cached messages available`() = runTest {
        val dao = InMemoryMessageDao(listOf(cachedMessage("m1")))
        val repository = MailRepository(FakeJmapApi { throw IOException("offline") }, dao)

        val page = repository.getMessagesPage(FOLDER_ID, position = 0, limit = PAGE_SIZE, forceRefresh = true).getOrThrow()

        assertEquals(1, page.items.size)
        assertTrue(page.fromCache)
        assertEquals(1, dao.messages.size)
    }

    private fun emptyQuery(position: Int) = EmailQueryResult(
        ids = emptyList(),
        position = position,
        total = 0,
        queryState = "query-state"
    )

    private fun cachedMessage(id: String) = MessageEntity(
        id = id,
        threadId = "thread-$id",
        folderId = FOLDER_ID,
        accountId = ACCOUNT_ID,
        fromName = "Sender",
        fromEmail = "sender@example.test",
        subject = "Subject $id",
        snippet = "Snippet",
        date = Date(1_700_000_000_000),
        isUnread = false,
        isStarred = false,
        isImportant = false,
        hasAttachments = false,
        size = 10,
        textBody = null,
        htmlBody = null,
        syncedAt = System.currentTimeMillis()
    )

    private class FakeJmapApi(
        private val onQuery: (Int) -> EmailQueryResult
    ) : JmapApi {
        override suspend fun getSession() = JmapSession(
            apiUrl = "https://example.test/api",
            downloadUrl = "https://example.test/download",
            uploadUrl = "https://example.test/upload",
            accounts = mapOf(ACCOUNT_ID to JmapAccount(id = ACCOUNT_ID, name = "Test", isPersonal = true, isReadOnly = false)),
            primaryAccounts = PrimaryAccounts(mail = ACCOUNT_ID)
        )

        override suspend fun getMailboxes(accountId: String?) = emptyList<JmapMailbox>()

        override suspend fun queryEmails(
            mailboxId: String?,
            accountId: String?,
            position: Int,
            limit: Int,
            filter: Map<String, Any>?,
            searchText: String?
        ) = onQuery(position)

        override suspend fun getEmails(ids: List<String>, accountId: String?, properties: List<String>?) =
            emptyList<JmapEmail>()

        override suspend fun queryAndGetEmails(
            mailboxId: String?,
            accountId: String?,
            position: Int,
            limit: Int,
            filter: Map<String, Any>?,
            searchText: String?,
            properties: List<String>?
        ) = onQuery(position) to emptyList<JmapEmail>()

        override suspend fun updateEmailKeywords(emailId: String, keywords: Map<String, Boolean>, accountId: String?) =
            error("not used")

        override suspend fun deleteEmail(emailId: String, accountId: String?) = error("not used")

        override suspend fun moveEmail(emailId: String, fromMailboxId: String, toMailboxId: String, accountId: String?) =
            error("not used")

        override suspend fun downloadAttachment(blobId: String, accountId: String?) = error("not used")

        override suspend fun uploadAttachment(data: ByteArray, mimeType: String, filename: String, accountId: String?) =
            error("not used")

        override suspend fun sendEmail(
            from: String,
            to: List<String>,
            subject: String,
            body: String,
            attachments: List<Attachment>,
            draftId: String?,
            accountId: String?
        ) = error("not used")

        override suspend fun getEmailSubmission(submissionId: String, accountId: String?) = error("not used")

        override suspend fun saveDraft(
            from: String,
            to: List<String>,
            subject: String,
            body: String,
            attachments: List<Attachment>,
            draftId: String?,
            accountId: String?
        ) = error("not used")
    }

    private class InMemoryMessageDao(initial: List<MessageEntity>) : MessageDao {
        val messages = initial.toMutableList()

        override fun getMessagesByFolder(folderId: String, accountId: String): Flow<List<MessageEntity>> =
            flowOf(messages.filter { it.folderId == folderId && it.accountId == accountId })

        override suspend fun getMessagesByFolderPaged(
            folderId: String,
            accountId: String,
            limit: Int,
            offset: Int
        ): List<MessageEntity> = messages
            .filter { it.folderId == folderId && it.accountId == accountId }
            .sortedByDescending { it.date }
            .drop(offset)
            .take(limit)

        override suspend fun getMessageCountByFolder(folderId: String, accountId: String): Int =
            messages.count { it.folderId == folderId && it.accountId == accountId }

        override suspend fun getLatestSyncedAtByFolder(folderId: String, accountId: String): Long? = messages
            .filter { it.folderId == folderId && it.accountId == accountId }
            .maxOfOrNull { it.syncedAt }

        override suspend fun getMessageById(messageId: String): MessageEntity? =
            messages.firstOrNull { it.id == messageId }

        override suspend fun insertMessage(message: MessageEntity) {
            messages.removeAll { it.id == message.id }
            messages.add(message)
        }

        override suspend fun insertMessages(messages: List<MessageEntity>) = messages.forEach { insertMessage(it) }

        override suspend fun updateMessage(message: MessageEntity) = insertMessage(message)

        override suspend fun deleteMessage(message: MessageEntity) {
            messages.removeAll { it.id == message.id }
        }

        override suspend fun deleteMessagesByFolder(folderId: String, accountId: String) {
            messages.removeAll { it.folderId == folderId && it.accountId == accountId }
        }

        override suspend fun deleteMessagesByAccount(accountId: String) {
            messages.removeAll { it.accountId == accountId }
        }

        override suspend fun updateReadStatus(messageId: String, isUnread: Boolean) {
            val index = messages.indexOfFirst { it.id == messageId }
            if (index >= 0) messages[index] = messages[index].copy(isUnread = isUnread)
        }

        override suspend fun updateStarredStatus(messageId: String, isStarred: Boolean) {
            val index = messages.indexOfFirst { it.id == messageId }
            if (index >= 0) messages[index] = messages[index].copy(isStarred = isStarred)
        }

        override suspend fun searchMessagesFts(
            accountId: String,
            matchQuery: String,
            folderId: String?,
            senderLike: String?,
            unreadOnly: Boolean,
            hasAttachments: Boolean,
            starredOnly: Boolean,
            importantOnly: Boolean,
            dateFromMillis: Long?,
            limit: Int,
            offset: Int
        ) = error("not used")

        override suspend fun searchMessagesFiltered(
            accountId: String,
            folderId: String?,
            senderLike: String?,
            unreadOnly: Boolean,
            hasAttachments: Boolean,
            starredOnly: Boolean,
            importantOnly: Boolean,
            dateFromMillis: Long?,
            limit: Int,
            offset: Int
        ) = error("not used")
    }

    private companion object {
        const val ACCOUNT_ID = "account-1"
        const val FOLDER_ID = "folder-inbox"
        const val PAGE_SIZE = 50
    }
}
