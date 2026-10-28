package com.mobilemail.data.repository

import com.mobilemail.domain.common.Result
import com.mobilemail.domain.common.runCatchingSuspend
import com.mobilemail.data.jmap.JmapApi
import com.mobilemail.domain.repository.IAttachmentRepository

class AttachmentRepository(
    private val jmapClient: JmapApi
) : IAttachmentRepository {
    override suspend fun downloadAttachment(blobId: String): Result<ByteArray> = runCatchingSuspend {
        val session = jmapClient.getSession()
        val accountId = session.primaryAccounts?.mail 
            ?: session.accounts.keys.firstOrNull()
            ?: error("AccountId не найден")
        
        jmapClient.downloadAttachment(blobId, accountId)
    }
}
