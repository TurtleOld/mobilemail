package com.mobilemail.data.repository

import com.mobilemail.data.common.Result
import com.mobilemail.data.common.runCatchingSuspend
import com.mobilemail.data.jmap.JmapApi
import com.mobilemail.data.model.Attachment
import com.mobilemail.data.model.EmailSubmissionStatus

class ComposeRepository(
    private val jmapClient: JmapApi
) {
    suspend fun sendMessage(
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
            ?: throw IllegalStateException("AccountId не найден")

        if (to.isEmpty()) {
            throw IllegalArgumentException("Не указан получатель")
        }

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

    suspend fun getEmailSubmissionStatus(
        submissionId: String
    ): Result<EmailSubmissionStatus> = runCatchingSuspend {
        val session = jmapClient.getSession()
        val accountId = session.primaryAccounts?.mail
            ?: session.accounts.keys.firstOrNull()
            ?: throw IllegalStateException("AccountId не найден")

        jmapClient.getEmailSubmission(submissionId = submissionId, accountId = accountId)
    }

    suspend fun uploadAttachment(
        data: ByteArray,
        mimeType: String,
        filename: String
    ): Result<Attachment> = runCatchingSuspend {
        val session = jmapClient.getSession()
        val accountId = session.primaryAccounts?.mail
            ?: session.accounts.keys.firstOrNull()
            ?: throw IllegalStateException("AccountId не найден")

        jmapClient.uploadAttachment(data, mimeType, filename, accountId)
    }

    suspend fun saveDraft(
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
            ?: throw IllegalStateException("AccountId не найден")

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
}