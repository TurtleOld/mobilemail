package com.mobilemail.ui.messagedetail.content

private val disallowedHtmlTagsRegex = Regex(
    pattern = "(?is)<(script|iframe|object|embed|base|meta|link)(?:\\s[^>]*)?>.*?</\\1\\s*>|<(script|iframe|object|embed|base|meta|link)(?:\\s[^>]*)?/?>"
)

private val inlineEventHandlersRegex = Regex(pattern = "(?i)\\son[a-z]+\\s*=\\s*(['\"]).*?\\1")

private val javascriptHrefRegex = Regex(pattern = "(?i)(href|src)\\s*=\\s*(['\"])\\s*javascript:[^'\"]*\\2")

internal fun sanitizeHtmlForWebView(rawHtml: String): String {
    val withoutDangerousTags = rawHtml.replace(disallowedHtmlTagsRegex, "")
    val withoutInlineHandlers = withoutDangerousTags.replace(inlineEventHandlersRegex, "")
    return withoutInlineHandlers.replace(javascriptHrefRegex, "$1=\"#\"")
}
