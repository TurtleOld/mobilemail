package com.mobilemail.ui.messagedetail.content

internal object HtmlDocumentComposer {
    // Gentle, non-destructive styling. We deliberately do NOT force
    // `table-layout: fixed`, a universal `max-width: 100%`, or `overflow-x: hidden`:
    // those mangle designed (table-based) emails — collapsing columns into a
    // one-word-per-line ladder or clipping content that runs past the screen.
    // Instead we let the message keep its natural width and rely on the WebView's
    // shrink-to-fit (wide viewport + overview mode) to scale it down to the screen.
    // Here we only tame the two things that genuinely break reading: oversized
    // media and unbreakable long tokens (URLs, hashes) overflowing their box.
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

    // Reflow viewport for simple/plain emails: lay out at screen width so text
    // wraps comfortably at full size.
    private const val deviceWidthViewport =
        "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes\">"

    private val existingViewportRegex = Regex("(?i)<meta[^>]*name\\s*=\\s*['\"]viewport['\"]")

    // Fixed pixel widths declared on containers, e.g. width="600" or style="width:640px".
    private val fixedPixelWidthRegex = Regex("(?i)width\\s*[:=]\\s*[\"']?\\s*(\\d{2,4})\\s*(?:px)?\\b")

    // Marketing emails are built on a fixed-width container (~600px). Below this we
    // treat the layout as fluid/plain and reflow it; at or above it we keep the
    // design intact and shrink-to-fit instead (declaring the viewport == content
    // width is the only thing that makes overview mode actually scale it down).
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
        // A responsive email already knows how to lay itself out — respect its viewport.
        if (existingViewportRegex.containsMatchIn(sourceHtml)) return ""

        val designWidth = detectFixedLayoutWidth(sourceHtml)
        return if (designWidth != null) {
            // Shrink-to-fit: viewport == design width so overview mode scales the
            // whole email down to the screen instead of clipping the overflow.
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
