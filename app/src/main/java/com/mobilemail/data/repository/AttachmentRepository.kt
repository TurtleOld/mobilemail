package com.mobilemail.data.repository

import com.mobilemail.data.common.Result
import com.mobilemail.data.common.runCatchingSuspend
import com.mobilemail.data.jmap.JmapApi

class AttachmentRepository(
    private val jmapClient: JmapApi
) {
    suspend fun downloadAttachment(blobId: String): Result<ByteArray> = runCatchingSuspend {
        val session = jmapClient.getSession()
        val accountId = session.primaryAccounts?.mail 
            ?: session.accounts.keys.firstOrNull()
            ?: throw IllegalStateException("AccountId не найден")
        
        jmapClient.downloadAttachment(blobId, accountId)
    }
}
