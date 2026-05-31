package com.mobilemail.domain.repository

import com.mobilemail.data.common.Result

interface IAttachmentRepository {
    suspend fun downloadAttachment(blobId: String): Result<ByteArray>
}
