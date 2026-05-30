package com.mobilemail.ui.messagedetail.content

internal object HtmlDocumentComposer {
    private val responsiveStyleBlock = """
        <style>
            html, body {
                max-width: 100% !important;
                overflow-x: hidden !important;
            }
            body {
                margin: 0 !important;
                padding: 8px !important;
                word-wrap: break-word !important;
                overflow-wrap: anywhere !important;
                min-width: 0 !important;
            }
            * {
                box-sizing: border-box !important;
                max-width: 100% !important;
                min-width: 0 !important;
            }
            img, video, iframe {
                max-width: 100% !important;
                height: auto !important;
            }
            table {
                width: 100% !important;
                table-layout: fixed !important;
            }
            td, th, pre, code, blockquote {
                word-break: break-word !important;
                white-space: pre-wrap !important;
            }
            [style*="width"] {
                max-width: 100% !important;
            }
        </style>
    """.trimIndent()

    private const val viewportMeta =
        "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes\">"

    fun composeResponsiveDocument(sourceHtml: String): String {
        return when {
            sourceHtml.contains("<head>", ignoreCase = true) -> {
                sourceHtml.replace(
                    "<head>",
                    "<head>$viewportMeta$responsiveStyleBlock",
                    ignoreCase = true
                )
            }
            sourceHtml.contains("<html>", ignoreCase = true) -> {
                sourceHtml.replace(
                    "<html>",
                    "<html><head>$viewportMeta$responsiveStyleBlock</head>",
                    ignoreCase = true
                )
            }
            else -> {
                "<html><head>$viewportMeta$responsiveStyleBlock</head><body>$sourceHtml</body></html>"
            }
        }
    }
}
