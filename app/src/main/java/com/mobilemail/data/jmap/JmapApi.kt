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
}
