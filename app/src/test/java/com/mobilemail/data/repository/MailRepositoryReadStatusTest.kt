package com.mobilemail.data.repository

import com.mobilemail.data.jmap.JmapApi
import com.mobilemail.data.local.dao.FolderDao
import com.mobilemail.data.local.dao.MessageDao
import com.mobilemail.data.local.dao.PendingOperationDao
import com.mobilemail.data.local.entity.FolderEntity
import com.mobilemail.data.local.entity.MessageEntity
import com.mobilemail.data.local.entity.PendingOperationEntity
import com.mobilemail.data.model.Attachment
import com.mobilemail.data.model.EmailAddress
import com.mobilemail.data.model.EmailQueryResult
import com.mobilemail.data.model.EmailSubmissionStatus
import com.mobilemail.data.model.JmapAccount
import com.mobilemail.data.model.JmapEmail
import com.mobilemail.data.model.JmapMailbox
import com.mobilemail.data.model.JmapSession
import com.mobilemail.data.model.PrimaryAccounts
import com.mobilemail.data.sync.OfflineQueueManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class MailRepositoryReadStatusTest {

    private val folderId = "inbox"
    private val accountId = "acc"

    @Test
    fun `server read status replaces stale cached unread flag`() = runTest {
        val messageDao = FakeMessageDao()
        messageDao.store(entity(id = "m-1", isUnread = true))
        val repository = repository(
            serverEmails = listOf(email(id = "m-1", seen = true)),
            messageDao = messageDao
        )

        val messages = repository.getMessages(folderId).getOrThrow()

        assertEquals(false, messages.single().flags.unread)
        assertEquals(false, messageDao.getStored("m-1")?.isUnread)
    }

    @Test
    fun `pending local mark-read overrides server unread in the list`() = runTest {
        val messageDao = FakeMessageDao()
        messageDao.store(entity(id = "m-1", isUnread = true))
        val repository = repository(
            serverEmails = listOf(email(id = "m-1", seen = false)),
            messageDao = messageDao,
            pendingOperations = listOf(readOperation(id = 1, isRead = true, status = OfflineQueueManager.STATUS_PENDING))
        )

        val messages = repository.getMessages(folderId).getOrThrow()

        assertEquals(false, messages.single().flags.unread)
        // The cache mirrors the server; the pending intent is applied when reading it.
        assertEquals(true, messageDao.getStored("m-1")?.isUnread)
    }

    @Test
    fun `permanently rejected local action no longer overrides the server value`() = runTest {
        val repository = repository(
            serverEmails = listOf(email(id = "m-1", seen = false)),
            pendingOperations = listOf(
                readOperation(id = 1, isRead = true, status = OfflineQueueManager.STATUS_PERMANENT_FAILED)
            )
        )

        val messages = repository.getMessages(folderId).getOrThrow()

        assertEquals(true, messages.single().flags.unread)
    }

    @Test
    fun `opening a message uses the server status instead of the cached flag`() = runTest {
        val messageDao = FakeMessageDao()
        messageDao.store(entity(id = "m-1", isUnread = true))
        val repository = repository(
            serverEmails = listOf(email(id = "m-1", seen = true)),
            messageDao = messageDao
        )

        val message = repository.getMessage("m-1").getOrThrow()

        assertEquals(false, message.flags.unread)
    }

    @Test
    fun `opening a message keeps a pending local unread intent`() = runTest {
        val repository = repository(
            serverEmails = listOf(email(id = "m-1", seen = true)),
            pendingOperations = listOf(
                readOperation(id = 1, isRead = false, status = OfflineQueueManager.STATUS_PENDING)
            )
        )

        val message = repository.getMessage("m-1").getOrThrow()

        assertEquals(true, message.flags.unread)
        assertTrue(repository.hasPendingReadStatusChange("m-1"))
    }

    private fun repository(
        serverEmails: List<JmapEmail>,
        messageDao: MessageDao = FakeMessageDao(),
        pendingOperations: List<PendingOperationEntity> = emptyList()
    ) = MailRepository(
        jmapClient = FakeJmapApi(serverEmails),
        messageDao = messageDao,
        folderDao = FakeFolderDao(),
        localAccountId = "https://example.test|user@example.test",
        pendingOperationDao = FakePendingOperationDao(pendingOperations)
    )

    private fun email(id: String, seen: Boolean) = JmapEmail(
        id = id,
        threadId = "t-$id",
        mailboxIds = mapOf(folderId to true),
        keywords = if (seen) mapOf("\$seen" to true) else emptyMap(),
        size = 10,
        receivedAt = "2026-01-01T10:00:00Z",
        subject = "Subject $id",
        from = listOf(EmailAddress(name = "Sender", email = "sender@example.test"))
    )

    private fun entity(id: String, isUnread: Boolean) = MessageEntity(
        id = id,
        threadId = "t-$id",
        folderId = folderId,
        accountId = accountId,
        localAccountId = "https://example.test|user@example.test",
        fromName = "Sender",
        fromEmail = "sender@example.test",
        subject = "Subject $id",
        snippet = "",
        date = Date(),
        isUnread = isUnread,
        isStarred = false,
        isImportant = false,
        hasAttachments = false,
        size = 10,
        textBody = null,
        htmlBody = null
    )

    private fun readOperation(id: Long, isRead: Boolean, status: String) = PendingOperationEntity(
        id = id,
        type = OfflineQueueManager.TYPE_MARK_READ,
        server = "https://example.test",
        email = "user@example.test",
        accountId = accountId,
        payload = """{"messageId":"m-1","isRead":$isRead}""",
        status = status,
        attemptCount = 0,
        lastError = null,
        createdAt = 1,
        updatedAt = 1
    )

    private class FakeMessageDao : MessageDao {
        private val stored = mutableMapOf<String, MessageEntity>()

        fun store(message: MessageEntity) {
            stored[message.id] = message
        }

        fun getStored(id: String): MessageEntity? = stored[id]

        override fun getMessagesByFolder(folderId: String, localAccountId: String): Flow<List<MessageEntity>> =
            flowOf(stored.values.toList())

        override suspend fun getMessagesByFolderPaged(
            folderId: String,
            localAccountId: String,
            limit: Int,
            offset: Int
        ): List<MessageEntity> = stored.values
            .filter { it.folderId == folderId && it.localAccountId == localAccountId }
            .sortedByDescending { it.date }
            .drop(offset)
            .take(limit)

        override suspend fun getMessageCountByFolder(folderId: String, localAccountId: String): Int =
            stored.values.count { it.folderId == folderId && it.localAccountId == localAccountId }

        override suspend fun getLatestSyncedAtByFolder(folderId: String, localAccountId: String): Long? =
            stored.values.filter { it.folderId == folderId && it.localAccountId == localAccountId }.maxOfOrNull { it.syncedAt }

        override suspend fun getMessageById(messageId: String, localAccountId: String): MessageEntity? = stored[messageId]

        override suspend fun insertMessage(message: MessageEntity) {
            stored[message.id] = message
        }

        override suspend fun insertMessages(messages: List<MessageEntity>) {
            messages.forEach { stored[it.id] = it }
        }

        override suspend fun updateMessage(message: MessageEntity) {
            stored[message.id] = message
        }

        override suspend fun deleteMessage(message: MessageEntity) {
            stored.remove(message.id)
        }

        override suspend fun deleteMessagesByFolder(folderId: String, localAccountId: String) = Unit

        override suspend fun deleteMessagesByAccount(localAccountId: String) = Unit

        override suspend fun updateReadStatus(messageId: String, localAccountId: String, isUnread: Boolean) {
            stored[messageId]?.let { stored[messageId] = it.copy(isUnread = isUnread) }
        }

        override suspend fun updateStarredStatus(messageId: String, localAccountId: String, isStarred: Boolean) = Unit

        override suspend fun searchMessagesFts(
            localAccountId: String,
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
        ): List<MessageEntity> = emptyList()

        override suspend fun searchMessagesFiltered(
            localAccountId: String,
            folderId: String?,
            senderLike: String?,
            unreadOnly: Boolean,
            hasAttachments: Boolean,
            starredOnly: Boolean,
            importantOnly: Boolean,
            dateFromMillis: Long?,
            limit: Int,
            offset: Int
        ): List<MessageEntity> = emptyList()
    }

    private class FakeFolderDao : FolderDao {
        override fun getFoldersByAccount(localAccountId: String): Flow<List<FolderEntity>> = flowOf(emptyList())

        override suspend fun getFoldersByAccountSync(localAccountId: String): List<FolderEntity> = emptyList()

        override suspend fun getFolderById(folderId: String, localAccountId: String): FolderEntity? = null

        override suspend fun updateFolderSyncState(
            folderId: String,
            localAccountId: String,
            queryState: String?,
            syncedAt: Long
        ) = Unit

        override suspend fun insertFolder(folder: FolderEntity) = Unit

        override suspend fun insertFolders(folders: List<FolderEntity>) = Unit

        override suspend fun updateFolder(folder: FolderEntity) = Unit

        override suspend fun deleteFolder(folder: FolderEntity) = Unit

        override suspend fun deleteFoldersByAccount(localAccountId: String) = Unit
    }

    private class FakePendingOperationDao(
        private val activeReadStatusOperations: List<PendingOperationEntity>
    ) : PendingOperationDao {
        override suspend fun getActiveReadStatusOperations(): List<PendingOperationEntity> =
            activeReadStatusOperations

        override suspend fun insert(operation: PendingOperationEntity): Long = operation.id

        override suspend fun getProcessable(limit: Int): List<PendingOperationEntity> = emptyList()

        override suspend fun updateStatus(
            id: Long,
            status: String,
            attemptCount: Int,
            lastError: String?,
            updatedAt: Long
        ) = Unit

        override suspend fun deleteById(id: Long) = Unit

        override suspend fun resetRunning(updatedAt: Long) = Unit

        override suspend fun resetInterruptedSubmissions(updatedAt: Long) = Unit

        override suspend fun getUnknownOutcomeCount(): Int = 0

        override suspend fun deleteByAccount(server: String, email: String) = Unit

        override suspend fun getPendingCount(): Int = 0


        override suspend fun getPendingCountForAccount(server: String, email: String): Int =
            getAll().count { it.server == server && it.email == email && it.status in listOf("pending", "failed") }

        override suspend fun getFailedCountForAccount(server: String, email: String): Int =
            getAll().count { it.server == server && it.email == email && it.status == "failed" }

        override suspend fun getPermanentFailedCountForAccount(server: String, email: String): Int =
            getAll().count { it.server == server && it.email == email && it.status == "permanent_failed" }

        override suspend fun getPermanentFailedCount(): Int = 0

        override fun observeAll(): Flow<List<PendingOperationEntity>> = flowOf(emptyList())

        override suspend fun getById(id: Long): PendingOperationEntity? = null

        override suspend fun getUnknownOutcomeCountForAccount(server: String, email: String): Int =
            getAll().count { it.server == server && it.email == email && it.status == "unknown_outcome" }

        override suspend fun getAll(): List<PendingOperationEntity> = activeReadStatusOperations

        override suspend fun getFailedOperations(server: String, email: String): List<PendingOperationEntity> = emptyList()

        override suspend fun delete(id: Long) = Unit

        override suspend fun retryById(id: Long, updatedAt: Long) = Unit

        override suspend fun retryAllFailed(server: String, email: String, updatedAt: Long) = Unit

        override suspend fun clearFailed(server: String, email: String) = Unit
    }

    private class FakeJmapApi(
        private val emails: List<JmapEmail>,
        private val accountId: String = "acc"
    ) : JmapApi {
        override suspend fun getSession(): JmapSession = JmapSession(
            apiUrl = "https://example.test/api",
            downloadUrl = "https://example.test/download",
            uploadUrl = "https://example.test/upload",
            accounts = mapOf(
                accountId to JmapAccount(
                    id = accountId,
                    name = "User",
                    username = "user@example.test",
                    isPersonal = true,
                    isReadOnly = false
                )
            ),
            primaryAccounts = PrimaryAccounts(mail = accountId)
        )

        override suspend fun getMailboxes(accountId: String?): List<JmapMailbox> = emptyList()

        override suspend fun queryEmails(
            mailboxId: String?,
            accountId: String?,
            position: Int,
            limit: Int,
            filter: Map<String, Any>?,
            searchText: String?
        ): EmailQueryResult = EmailQueryResult(
            ids = emails.map { it.id },
            position = position,
            queryState = "state"
        )

        override suspend fun getEmails(
            ids: List<String>,
            accountId: String?,
            properties: List<String>?
        ): List<JmapEmail> = emails.filter { it.id in ids }

        override suspend fun queryAndGetEmails(
            mailboxId: String?,
            accountId: String?,
            position: Int,
            limit: Int,
            filter: Map<String, Any>?,
            searchText: String?,
            properties: List<String>?
        ): Pair<EmailQueryResult, List<JmapEmail>> =
            queryEmails(mailboxId, accountId, position, limit, filter, searchText) to emails

        override suspend fun updateEmailKeywords(
            emailId: String,
            keywords: Map<String, Boolean>,
            accountId: String?
        ): Boolean = error("unused")

        override suspend fun deleteEmail(emailId: String, accountId: String?): Boolean = error("unused")

        override suspend fun moveEmail(
            emailId: String,
            fromMailboxId: String,
            toMailboxId: String,
            accountId: String?
        ): Boolean = error("unused")

        override suspend fun downloadAttachment(blobId: String, accountId: String?): ByteArray = error("unused")

        override suspend fun uploadAttachment(
            data: ByteArray,
            mimeType: String,
            filename: String,
            accountId: String?
        ): Attachment = error("unused")

        override suspend fun sendEmail(
            from: String,
            to: List<String>,
            subject: String,
            body: String,
            attachments: List<Attachment>,
            draftId: String?,
            accountId: String?,
        onBeforeSubmission: (suspend () -> Unit)?
        ): String = error("unused")

        override suspend fun getEmailSubmission(
            submissionId: String,
            accountId: String?
        ): EmailSubmissionStatus = error("unused")

        override suspend fun saveDraft(
            from: String,
            to: List<String>,
            subject: String,
            body: String,
            attachments: List<Attachment>,
            draftId: String?,
            accountId: String?
        ): String = error("unused")
    }
}
