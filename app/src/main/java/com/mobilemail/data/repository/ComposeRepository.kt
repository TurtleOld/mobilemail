package com.mobilemail.data.repository

import com.mobilemail.data.common.Result
import com.mobilemail.data.common.runCatchingSuspend
import com.mobilemail.data.jmap.JmapApi
import com.mobilemail.data.model.Attachment
import com.mobilemail.data.model.EmailSubmissionStatus
import com.mobilemail.domain.model.toDomain
import com.mobilemail.domain.model.toData
import com.mobilemail.domain.repository.IComposeRepository
import com.mobilemail.domain.model.Attachment as DomainAttachment
import com.mobilemail.domain.model.EmailSubmissionStatus as DomainEmailSubmissionStatus

class ComposeRepository(
    private val jmapClient: JmapApi
) : IComposeRepository {

    suspend fun sendMessageData(
        from: String,
        to: List<String>,
        subject: String,
        body: String,
        attachments: List<Attachment> = emptyList(),
        draftId: String? = null
    ): Result<String> = runCatchingSuspend {
        val session = jmapClient.getSession()
        val accountId = session.primaryAccounts?.mail
            ?: session.accounts.keys.firstOrNull()
            ?: error("AccountId не найден")

        require(to.isNotEmpty()) { "Не указан получатель" }

        jmapClient.sendEmail(
            from = from,
            to = to,
            subject = subject,
            body = body,
            attachments = attachments,
            draftId = draftId,
            accountId = accountId
        )
    }

    suspend fun getEmailSubmissionStatusData(
        submissionId: String
    ): Result<EmailSubmissionStatus> = runCatchingSuspend {
        val session = jmapClient.getSession()
        val accountId = session.primaryAccounts?.mail
            ?: session.accounts.keys.firstOrNull()
            ?: error("AccountId не найден")

        jmapClient.getEmailSubmission(submissionId = submissionId, accountId = accountId)
    }

    suspend fun uploadAttachmentData(
        data: ByteArray,
        mimeType: String,
        filename: String
    ): Result<Attachment> = runCatchingSuspend {
        val session = jmapClient.getSession()
        val accountId = session.primaryAccounts?.mail
            ?: session.accounts.keys.firstOrNull()
            ?: error("AccountId не найден")

        jmapClient.uploadAttachment(data, mimeType, filename, accountId)
    }

    suspend fun saveDraftData(
        from: String,
        to: List<String>,
        subject: String,
        body: String,
        attachments: List<Attachment> = emptyList(),
        draftId: String? = null
    ): Result<String> = runCatchingSuspend {
        val session = jmapClient.getSession()
        val accountId = session.primaryAccounts?.mail
            ?: session.accounts.keys.firstOrNull()
            ?: error("AccountId не найден")

        jmapClient.saveDraft(
            from = from,
            to = to,
            subject = subject,
            body = body,
            attachments = attachments,
            draftId = draftId,
            accountId = accountId
        )
    }

    // IComposeRepository

    override suspend fun sendMessage(
        from: String,
        to: List<String>,
        subject: String,
        body: String,
        attachments: List<DomainAttachment>,
        draftId: String?
    ): Result<String> = sendMessageData(from, to, subject, body, attachments.map { it.toData() }, draftId)

    override suspend fun saveDraft(
        from: String,
        to: List<String>,
        subject: String,
        body: String,
        attachments: List<DomainAttachment>,
        draftId: String?
    ): Result<String> = saveDraftData(from, to, subject, body, attachments.map { it.toData() }, draftId)

    override suspend fun getEmailSubmissionStatus(submissionId: String): Result<DomainEmailSubmissionStatus> =
        getEmailSubmissionStatusData(submissionId).map { it.toDomain() }

    override suspend fun uploadAttachment(
        data: ByteArray,
        mimeType: String,
        filename: String
    ): Result<DomainAttachment> = uploadAttachmentData(data, mimeType, filename).map { it.toDomain() }
}
