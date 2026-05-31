package com.mobilemail.domain.repository

import com.mobilemail.domain.common.Result

interface IAttachmentRepository {
    suspend fun downloadAttachment(blobId: String): Result<ByteArray>
}
