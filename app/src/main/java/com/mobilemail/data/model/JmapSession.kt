package com.mobilemail.data.model

import com.google.gson.annotations.SerializedName

data class JmapSession(
    @SerializedName('apiUrl')
    val apiUrl: String,
    @SerializedName('downloadUrl')
    val downloadUrl: String,
    @SerializedName('uploadUrl')
    val uploadUrl: String,
    @SerializedName('eventSourceUrl')
    val eventSourceUrl: String? = null,
    @SerializedName('accounts')
    val accounts: Map<String, JmapAccount>,
    @SerializedName('primaryAccounts')
    val primaryAccounts: PrimaryAccounts? = null,
    @SerializedName('capabilities')
    val capabilities: Map<String, Any>? = null
)

data class JmapAccount(
    @SerializedName('id')
    val id: String,
    @SerializedName('name')
    val name: String,
    @SerializedName('isPersonal')
    val isPersonal: Boolean,
    @SerializedName('isReadOnly')
    val isReadOnly: Boolean,
    @SerializedName('accountCapabilities')
    val accountCapabilities: Map<String, Any>? = null
)

data class PrimaryAccounts(
    @SerializedName('mail')
    val mail: String? = null
)
