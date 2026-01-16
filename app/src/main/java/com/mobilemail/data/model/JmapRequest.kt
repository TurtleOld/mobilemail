package com.mobilemail.data.model

data class JmapRequest(
    val using: List<String>,
    val methodCalls: List<List<Any>>
)

data class JmapResponse(
    val methodResponses: List<List<Any>>,
    val sessionState: String? = null
)
