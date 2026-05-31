package com.mobilemail.domain.repository

import com.mobilemail.domain.common.Result
import com.mobilemail.domain.model.Attachment
import com.mobilemail.domain.model.EmailSubmissionStatus

interface IComposeRepository {
    suspend fun sendMessage(
        from: String,
        to: List<String>,
        subject: String,
        body: String,
        attachments: List<Attachment> = emptyList(),
        draftId: String? = null
    ): Result<String>

    suspend fun saveDraft(
        from: String,
        to: List<String>,
        subject: String,
        body: String,
        attachments: List<Attachment> = emptyList(),
        draftId: String? = null
    ): Result<String>

    suspend fun getEmailSubmissionStatus(submissionId: String): Result<EmailSubmissionStatus>

    suspend fun uploadAttachment(
        data: ByteArray,
        mimeType: String,
        filename: String
    ): Result<Attachment>
}
