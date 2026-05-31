package com.mobilemail.domain.model

data class EmailSubmissionStatus(
    val id: String,
    val emailId: String? = null,
    val delivered: Boolean? = null,
    val failed: Boolean? = null,
    val lastStatusText: String? = null,
    val raw: Map<String, Any?> = emptyMap()
)
