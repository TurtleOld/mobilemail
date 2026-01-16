package com.mobilemail.data.model

import com.google.gson.annotations.SerializedName

data class JmapEmail(
    @SerializedName("id")
    val id: String,
    @SerializedName("threadId")
    val threadId: String,
    @SerializedName("mailboxIds")
    val mailboxIds: Map<String, Boolean>,
    @SerializedName("keywords")
    val keywords: Map<String, Boolean>? = null,
    @SerializedName("size")
    val size: Long,
    @SerializedName("receivedAt")
    val receivedAt: String,
    @SerializedName("hasAttachment")
    val hasAttachment: Boolean? = null,
    @SerializedName("preview")
    val preview: String? = null,
    @SerializedName("subject")
    val subject: String? = null,
    @SerializedName("from")
    val from: List<EmailAddress>? = null,
    @SerializedName("to")
    val to: List<EmailAddress>? = null,
    @SerializedName("cc")
    val cc: List<EmailAddress>? = null,
    @SerializedName("bcc")
    val bcc: List<EmailAddress>? = null,
    @SerializedName("bodyStructure")
    val bodyStructure: Any? = null,
    @SerializedName("bodyValues")
    val bodyValues: Map<String, BodyValue>? = null,
    @SerializedName("textBody")
    val textBody: List<BodyPart>? = null,
    @SerializedName("htmlBody")
    val htmlBody: List<BodyPart>? = null
)

data class EmailAddress(
    @SerializedName("name")
    val name: String? = null,
    @SerializedName("email")
    val email: String
)

data class BodyValue(
    @SerializedName("value")
    val value: String,
    @SerializedName("isEncodingProblem")
    val isEncodingProblem: Boolean? = null,
    @SerializedName("isTruncated")
    val isTruncated: Boolean? = null
)

data class BodyPart(
    @SerializedName("partId")
    val partId: String,
    @SerializedName("type")
    val type: String
)

data class EmailQueryResult(
    @SerializedName("ids")
    val ids: List<String>,
    @SerializedName("position")
    val position: Int,
    @SerializedName("total")
    val total: Int? = null,
    @SerializedName("queryState")
    val queryState: String? = null
)
