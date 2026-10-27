package com.mobilemail.data.model

data class JmapSession(
    val apiUrl: String,
    val downloadUrl: String,
    val uploadUrl: String,
    val eventSourceUrl: String? = null,
    val accounts: Map<String, JmapAccount>,
    val primaryAccounts: PrimaryAccounts? = null,
    val capabilities: Map<String, Any>? = null
)

data class JmapAccount(
    val id: String,
    val name: String,
    val username: String? = null,
    val isPersonal: Boolean,
    val isReadOnly: Boolean,
    val accountCapabilities: Map<String, Any>? = null
)

data class PrimaryAccounts(
    val mail: String? = null
)
