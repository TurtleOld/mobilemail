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

    /**
     * Email/query + Email/get в одном batch-запросе с back-reference #ids.
     * Эквивалентен паре queryEmails + getEmails, но делает один round-trip.
     */
    suspend fun queryAndGetEmails(
        mailboxId: String? = null,
        accountId: String? = null,
        position: Int = 0,
        limit: Int = 50,
        filter: Map<String, Any>? = null,
        searchText: String? = null,
        properties: List<String>? = null
    ): Pair<EmailQueryResult, List<JmapEmail>>

    /**
     * Sets keywords whose value is true and removes those whose value is false,
     * without touching other keywords already stored on the server.
     */
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
     * @param onBeforeSubmission вызывается непосредственно перед запросом создания
     *   EmailSubmission; офлайн-очередь использует его, чтобы сохранить точку восстановления
     *   на случай завершения процесса.
     * @return submissionId, по которому можно запросить статус доставки через [getEmailSubmission].
     * @throws com.mobilemail.data.jmap.SendOutcomeUnknownException если запрос мог быть принят
     *   сервером, а подтверждение потеряно.
     */
    @Suppress("LongParameterList")
    suspend fun sendEmail(
        from: String,
        to: List<String>,
        subject: String,
        body: String,
        attachments: List<com.mobilemail.data.model.Attachment> = emptyList(),
        draftId: String? = null,
        accountId: String? = null,
        onBeforeSubmission: (suspend () -> Unit)? = null
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
