package com.mobilemail.data.jmap

import com.mobilemail.data.model.EmailQueryResult
import com.mobilemail.data.model.JmapEmail
import com.mobilemail.data.model.JmapMailbox
import com.mobilemail.data.model.JmapSession

interface JmapApi {
    suspend fun getSession(): JmapSession

    suspend fun getMailboxes(accountId: String? = null): List<JmapMailbox>

    suspend fun queryEmails(
        mailboxId: String? = null,
        accountId: String? = null,
        position: Int = 0,
        limit: Int = 50,
        filter: Map<String, Any>? = null,
        searchText: String? = null
    ): EmailQueryResult

    suspend fun getEmails(
        ids: List<String>,
        accountId: String? = null,
        properties: List<String>? = null
    ): List<JmapEmail>

    suspend fun updateEmailKeywords(
        emailId: String,
        keywords: Map<String, Boolean>,
        accountId: String? = null
    ): Boolean

    suspend fun deleteEmail(
        emailId: String,
        accountId: String? = null
    ): Boolean

    suspend fun moveEmail(
        emailId: String,
        fromMailboxId: String,
        toMailboxId: String,
        accountId: String? = null
    ): Boolean

    suspend fun downloadAttachment(
        blobId: String,
        accountId: String? = null
    ): ByteArray

    suspend fun uploadAttachment(
        data: ByteArray,
        mimeType: String,
        filename: String,
        accountId: String? = null
    ): com.mobilemail.data.model.Attachment

    /**
     * Отправляет письмо через JMAP EmailSubmission/set.
     *
     * @return submissionId, по которому можно запросить статус доставки через [getEmailSubmission].
     */
    suspend fun sendEmail(
        from: String,
        to: List<String>,
        subject: String,
        body: String,
        attachments: List<com.mobilemail.data.model.Attachment> = emptyList(),
        draftId: String? = null,
        accountId: String? = null
    ): String

    /**
     * Возвращает информацию о доставке для submissionId (если сервер поддерживает EmailSubmission/get).
     */
    suspend fun getEmailSubmission(
        submissionId: String,
        accountId: String? = null
    ): com.mobilemail.data.model.EmailSubmissionStatus

    suspend fun saveDraft(
        from: String,
        to: List<String>,
        subject: String,
        body: String,
        attachments: List<com.mobilemail.data.model.Attachment> = emptyList(),
        draftId: String? = null,
        accountId: String? = null
    ): String
}
