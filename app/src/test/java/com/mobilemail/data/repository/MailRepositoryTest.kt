package com.mobilemail.data.repository

import com.mobilemail.data.jmap.JmapApi
import com.mobilemail.data.model.Attachment
import com.mobilemail.data.model.BodyPart
import com.mobilemail.data.model.BodyValue
import com.mobilemail.data.model.EmailAddress
import com.mobilemail.data.model.EmailQueryResult
import com.mobilemail.data.model.EmailSubmissionStatus
import com.mobilemail.data.model.JmapAccount
import com.mobilemail.data.model.JmapEmail
import com.mobilemail.data.model.JmapMailbox
import com.mobilemail.data.model.JmapSession
import com.mobilemail.data.model.PrimaryAccounts
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class MailRepositoryTest {

    @Test
    fun `folder page requests list fields only and body is loaded on open`() = runTest {
        val api = FakeJmapApi(ids = listOf("m1"))
        val repository = MailRepository(api)

        val page = repository.getMessagesPage(folderId = "inbox", position = 0, limit = 50).getOrThrow()

        assertEquals(listOf("m1"), page.items.map { it.id })
        val listProperties = api.emailGetProperties.single()
        assertTrue(listProperties.containsAll(LIST_PROPERTIES))
        assertFalse(listProperties.any { it in BODY_PROPERTIES })

        val message = repository.getMessage("m1").getOrThrow()

        assertEquals("body-m1", message.body.text)
        assertEquals(2, api.emailGetProperties.size)
        assertTrue(api.emailGetProperties[1].containsAll(BODY_PROPERTIES))
    }

    @Test
    fun `body cache evicts least recently used entries`() = runTest {
        val api = FakeJmapApi()
        val repository = MailRepository(api, bodyCacheSize = 2)

        repository.getMessage("m1").getOrThrow()
        repository.getMessage("m2").getOrThrow()
        repository.getMessage("m3").getOrThrow()
        api.offline = true

        assertEquals("m2", repository.getMessage("m2").getOrThrow().id)
        assertTrue(repository.getMessage("m1") is com.mobilemail.domain.common.Result.Error)
        assertEquals("m3", repository.getMessage("m3").getOrThrow().id)

    }

    @Test
    fun `message opens and updates after its cache entry is evicted`() = runTest {
        val api = FakeJmapApi()
        val repository = MailRepository(api, bodyCacheSize = 1)

        repository.getMessage("m1").getOrThrow()
        repository.getMessage("m2").getOrThrow()

        repository.updateMessageReadStatus("m1", isUnread = false)
        val reopened = repository.getMessage("m1").getOrThrow()

        assertEquals("m1", reopened.id)
        assertEquals(3, api.emailGetProperties.size)
    }

    private companion object {
        val LIST_PROPERTIES = listOf(
            "id", "threadId", "from", "subject", "receivedAt",
            "preview", "hasAttachment", "size", "keywords"
        )
        val BODY_PROPERTIES = listOf("bodyStructure", "bodyValues", "textBody", "htmlBody")
    }
}

private class FakeJmapApi(
    private val ids: List<String> = emptyList()
) : JmapApi {
    val emailGetProperties = mutableListOf<List<String>>()
    var offline = false

    private val session = JmapSession(
        apiUrl = "https://example.test/jmap",
        downloadUrl = "",
        uploadUrl = "",
        accounts = mapOf("acc1" to JmapAccount(id = "acc1", name = "Account", isPersonal = true, isReadOnly = false)),
        primaryAccounts = PrimaryAccounts(mail = "acc1")
    )

    override suspend fun getSession(): JmapSession = session

    override suspend fun getMailboxes(accountId: String?): List<JmapMailbox> = emptyList()

    override suspend fun queryEmails(
        mailboxId: String?,
        accountId: String?,
        position: Int,
        limit: Int,
        filter: Map<String, Any>?,
        searchText: String?
    ): EmailQueryResult = EmailQueryResult(ids = ids, position = position)

    override suspend fun getEmails(
        ids: List<String>,
        accountId: String?,
        properties: List<String>?
    ): List<JmapEmail> {
        if (offline) throw IOException("offline")
        emailGetProperties += properties.orEmpty()
        val includeBody = properties == null || properties.any { it in BODY_PROPERTY_NAMES }
        return ids.map { id ->
            JmapEmail(
                id = id,
                threadId = "thread-$id",
                mailboxIds = mapOf("inbox" to true),
                keywords = mapOf("\$seen" to true),
                size = 10,
                receivedAt = "2024-01-01T00:00:00Z",
                hasAttachment = false,
                preview = "preview-$id",
                subject = "Subject $id",
                from = listOf(EmailAddress(email = "sender@example.com")),
                to = listOf(EmailAddress(email = "me@example.com")),
                bodyStructure = if (includeBody) JSONObject() else null,
                bodyValues = if (includeBody) mapOf("1" to BodyValue(value = "body-$id")) else null,
                textBody = if (includeBody) listOf(BodyPart(partId = "1", type = "text/plain")) else null
            )
        }
    }

    override suspend fun queryAndGetEmails(
        mailboxId: String?,
        accountId: String?,
        position: Int,
        limit: Int,
        filter: Map<String, Any>?,
        searchText: String?,
        properties: List<String>?
    ): Pair<EmailQueryResult, List<JmapEmail>> = error("not used")

    override suspend fun updateEmailKeywords(
        emailId: String,
        keywords: Map<String, Boolean>,
        accountId: String?
    ): Boolean = error("not used")

    override suspend fun deleteEmail(emailId: String, accountId: String?): Boolean = error("not used")

    override suspend fun moveEmail(
        emailId: String,
        fromMailboxId: String,
        toMailboxId: String,
        accountId: String?
    ): Boolean = error("not used")

    override suspend fun downloadAttachment(blobId: String, accountId: String?): ByteArray = error("not used")

    override suspend fun uploadAttachment(
        data: ByteArray,
        mimeType: String,
        filename: String,
        accountId: String?
    ): Attachment = error("not used")

    override suspend fun sendEmail(
        from: String,
        to: List<String>,
        subject: String,
        body: String,
        attachments: List<Attachment>,
        draftId: String?,
        accountId: String?,
        onBeforeSubmission: (suspend () -> Unit)?
    ): String = error("not used")

    override suspend fun getEmailSubmission(
        submissionId: String,
        accountId: String?
    ): EmailSubmissionStatus = error("not used")

    override suspend fun saveDraft(
        from: String,
        to: List<String>,
        subject: String,
        body: String,
        attachments: List<Attachment>,
        draftId: String?,
        accountId: String?
    ): String = error("not used")

    private companion object {
        val BODY_PROPERTY_NAMES = setOf("bodyStructure", "bodyValues", "textBody", "htmlBody")
    }
}
