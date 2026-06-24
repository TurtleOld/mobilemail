package com.mobilemail.ui.messagedetail.content

internal object HtmlDocumentComposer {
    private val responsiveStyleBlock = """
        <style>
            img, video {
                max-width: 100%;
                height: auto;
            }
            pre, code {
                white-space: pre-wrap;
                word-wrap: break-word;
                overflow-wrap: break-word;
            }
            body {
                margin: 0;
                padding: 8px;
                word-wrap: break-word;
                overflow-wrap: break-word;
            }
        </style>
    """.trimIndent()

    private const val deviceWidthViewport =
        "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes\">"

    private val existingViewportRegex = Regex("(?i)<meta[^>]*name\\s*=\\s*['\"]viewport['\"]")

    private val fixedPixelWidthRegex = Regex("(?i)width\\s*[:=]\\s*[\"']?\\s*(\\d{2,4})\\s*(?:px)?\\b")

    private const val WIDE_LAYOUT_THRESHOLD_PX = 480
    private const val MAX_VIEWPORT_WIDTH_PX = 1280

    fun composeResponsiveDocument(sourceHtml: String): String {
        val headInjection = buildString {
            append(viewportMetaFor(sourceHtml))
            append(responsiveStyleBlock)
        }
        return when {
            sourceHtml.contains("<head>", ignoreCase = true) -> {
                sourceHtml.replace("<head>", "<head>$headInjection", ignoreCase = true)
            }
            sourceHtml.contains("<html>", ignoreCase = true) -> {
                sourceHtml.replace("<html>", "<html><head>$headInjection</head>", ignoreCase = true)
            }
            else -> {
                "<html><head>$headInjection</head><body>$sourceHtml</body></html>"
            }
        }
    }

    private fun viewportMetaFor(sourceHtml: String): String {
        if (existingViewportRegex.containsMatchIn(sourceHtml)) return ""

        val designWidth = detectFixedLayoutWidth(sourceHtml)
        return if (designWidth != null) {
            "<meta name=\"viewport\" content=\"width=$designWidth, maximum-scale=5.0, user-scalable=yes\">"
        } else {
            deviceWidthViewport
        }
    }

    private fun detectFixedLayoutWidth(sourceHtml: String): Int? {
        return fixedPixelWidthRegex.findAll(sourceHtml)
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .filter { it >= WIDE_LAYOUT_THRESHOLD_PX }
            .maxOrNull()
            ?.coerceAtMost(MAX_VIEWPORT_WIDTH_PX)
    }
}
