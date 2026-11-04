package com.mobilemail.data.local.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mobilemail.data.local.LocalAccountIdentity
import com.mobilemail.data.local.entity.FolderEntity
import com.mobilemail.data.local.entity.MessageEntity
import com.mobilemail.data.local.entity.PendingOperationEntity
import com.mobilemail.data.model.FolderRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class MessageAccountIsolationTest {
    private lateinit var database: AppDatabase

    private val server = "https://mail.example.com"
    private val firstAccountKey = LocalAccountIdentity.of(server, "first@example.com")
    private val secondAccountKey = LocalAccountIdentity.of(server, "second@example.com")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { AppDatabase.createMessageFtsTriggers(it.openHelper.writableDatabase) }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `two accounts of one server keep messages with the same server id`() = runBlocking {
        val messageDao = database.messageDao()
        messageDao.insertMessage(message(firstAccountKey, subject = "first account message"))
        messageDao.insertMessage(message(secondAccountKey, subject = "second account message"))

        val firstAccountMessages = messageDao.getMessagesByFolder(FOLDER_ID, firstAccountKey)
        val secondAccountMessages = messageDao.getMessagesByFolder(FOLDER_ID, secondAccountKey)

        assertEquals(listOf("first account message"), firstAccountMessages.first().map { it.subject })
        assertEquals(listOf("second account message"), secondAccountMessages.first().map { it.subject })
        assertEquals("first account message", messageDao.getMessageById(MESSAGE_ID, firstAccountKey)?.subject)
        assertEquals("second account message", messageDao.getMessageById(MESSAGE_ID, secondAccountKey)?.subject)
    }

    @Test
    fun `read status and search of one account do not touch another account`() = runBlocking {
        val messageDao = database.messageDao()
        messageDao.insertMessage(message(firstAccountKey, subject = "invoice from first"))
        messageDao.insertMessage(message(secondAccountKey, subject = "invoice from second"))

        messageDao.updateReadStatus(MESSAGE_ID, firstAccountKey, isUnread = false)

        assertFalse(messageDao.getMessageById(MESSAGE_ID, firstAccountKey)!!.isUnread)
        assertTrue(messageDao.getMessageById(MESSAGE_ID, secondAccountKey)!!.isUnread)

        val found = messageDao.searchMessagesFts(
            localAccountId = firstAccountKey,
            matchQuery = "invoice*",
            folderId = null,
            senderLike = null,
            unreadOnly = false,
            hasAttachments = false,
            starredOnly = false,
            importantOnly = false,
            dateFromMillis = null,
            limit = 50,
            offset = 0
        )

        assertEquals(listOf("invoice from first"), found.map { it.subject })
    }

    @Test
    fun `accounts on different servers keep the same server ids`() = runBlocking {
        val otherServerKey = LocalAccountIdentity.of("https://mail.other.example", "first@example.com")
        val messageDao = database.messageDao()
        messageDao.insertMessage(message(firstAccountKey, subject = "message of first server"))
        messageDao.insertMessage(message(otherServerKey, subject = "message of other server"))

        assertEquals("message of first server", messageDao.getMessageById(MESSAGE_ID, firstAccountKey)?.subject)
        assertEquals("message of other server", messageDao.getMessageById(MESSAGE_ID, otherServerKey)?.subject)

        messageDao.deleteMessagesByAccount(firstAccountKey)

        assertNull(messageDao.getMessageById(MESSAGE_ID, firstAccountKey))
        assertEquals("message of other server", messageDao.getMessageById(MESSAGE_ID, otherServerKey)?.subject)
    }

    @Test
    fun `folders with the same server id are isolated by account`() = runBlocking {
        val folderDao = database.folderDao()
        folderDao.insertFolder(folder(firstAccountKey, queryState = "first-state"))
        folderDao.insertFolder(folder(secondAccountKey, queryState = "second-state"))

        assertEquals("first-state", folderDao.getFolderById(FOLDER_ID, firstAccountKey)?.queryState)
        assertEquals("second-state", folderDao.getFolderById(FOLDER_ID, secondAccountKey)?.queryState)

        folderDao.deleteFoldersByAccount(firstAccountKey)

        assertNull(folderDao.getFolderById(FOLDER_ID, firstAccountKey))
        assertEquals("second-state", folderDao.getFolderById(FOLDER_ID, secondAccountKey)?.queryState)
    }

    @Test
    fun `queue cleanup removes only the logged out server account`() = runBlocking {
        val pendingOperationDao = database.pendingOperationDao()
        pendingOperationDao.insert(pendingOperation(server = server, email = "first@example.com"))
        pendingOperationDao.insert(
            pendingOperation(server = "https://mail.other.example", email = "first@example.com")
        )

        pendingOperationDao.deleteByAccount(server, "first@example.com")

        val remaining = pendingOperationDao.getProcessable()

        assertEquals(1, remaining.size)
        assertEquals("https://mail.other.example", remaining.single().server)
    }

    @Test
    fun `failed queue operations are retried and cleared per account`() = runBlocking {
        val pendingOperationDao = database.pendingOperationDao()
        pendingOperationDao.insert(
            pendingOperation(server = server, email = "first@example.com", status = "failed")
        )
        pendingOperationDao.insert(
            pendingOperation(server = server, email = "second@example.com", status = "failed")
        )

        pendingOperationDao.retryAllFailed(server, "first@example.com", updatedAt = 2_000)

        assertEquals(0, pendingOperationDao.getFailedCountForAccount(server, "first@example.com"))
        assertEquals(1, pendingOperationDao.getFailedCountForAccount(server, "second@example.com"))
        assertEquals(1, pendingOperationDao.getPendingCountForAccount(server, "first@example.com"))

        pendingOperationDao.clearFailed(server, "second@example.com")

        assertEquals(0, pendingOperationDao.getFailedOperations(server, "second@example.com").size)
        assertEquals(1, pendingOperationDao.getPendingCountForAccount(server, "first@example.com"))
    }

    private fun message(
        localAccountId: String,
        subject: String,
        id: String = MESSAGE_ID,
        accountId: String = SERVER_ACCOUNT_ID,
        folderId: String = FOLDER_ID
    ) = MessageEntity(
        localAccountId = localAccountId,
        id = id,
        threadId = "thread-1",
        folderId = folderId,
        accountId = accountId,
        fromName = "Sender",
        fromEmail = "sender@example.com",
        subject = subject,
        snippet = "invoice preview",
        date = Date(1_700_000_000_000),
        isUnread = true,
        isStarred = false,
        isImportant = false,
        hasAttachments = false,
        size = 1024,
        textBody = "invoice body",
        htmlBody = null,
        syncedAt = 1_700_000_000_000
    )

    private fun folder(localAccountId: String, queryState: String) = FolderEntity(
        localAccountId = localAccountId,
        id = FOLDER_ID,
        accountId = SERVER_ACCOUNT_ID,
        name = "Inbox",
        role = FolderRole.INBOX,
        unreadCount = 1,
        queryState = queryState,
        syncedAt = 1_700_000_000_000
    )

    private fun pendingOperation(
        server: String,
        email: String,
        status: String = "pending"
    ) = PendingOperationEntity(
        type = "mark_read",
        server = server,
        email = email,
        accountId = SERVER_ACCOUNT_ID,
        payload = "{}",
        status = status,
        attemptCount = 0,
        lastError = null,
        createdAt = 1_700_000_000_000,
        updatedAt = 1_700_000_000_000
    )

    companion object {
        private const val FOLDER_ID = "inbox"
        private const val MESSAGE_ID = "m-1"
        private const val SERVER_ACCOUNT_ID = "d"
    }
}
