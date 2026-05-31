package com.mobilemail.ui.messagedetail.content

import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal object HtmlWebViewHeightMeasurer {
    const val MIN_HEIGHT_DP = 160
    const val DEFAULT_HEIGHT_DP = 420
    const val HEIGHT_PADDING_DP = 24

    val measureScript: String = """
        (function() {
            var body = document.body || {};
            var doc = document.documentElement || {};
            return Math.max(
                body.scrollHeight || 0,
                body.offsetHeight || 0,
                doc.clientHeight || 0,
                doc.scrollHeight || 0,
                doc.offsetHeight || 0
            ).toString();
        })();
    """.trimIndent()

    fun parseMeasuredHeight(rawHeight: String?, density: Float): Dp? {
        val normalized = rawHeight?.trim()?.replace("\"", "") ?: return null
        val htmlHeightPx = normalized.toFloatOrNull() ?: return null
        if (htmlHeightPx <= 0f) return null
        return (htmlHeightPx / density).dp
            .plus(HEIGHT_PADDING_DP.dp)
            .coerceAtLeast(DEFAULT_HEIGHT_DP.dp)
    }

    fun scheduleHeightMeasurement(webView: WebView, onHeight: (Dp) -> Unit) {
        val measure: (WebView) -> Unit = { currentWebView ->
            currentWebView.evaluateJavascript(measureScript) { rawHeight ->
                parseMeasuredHeight(rawHeight, currentWebView.resources.displayMetrics.density)?.let(onHeight)
            }
        }
        measure(webView)
        webView.post { measure(webView) }
        webView.postDelayed({ measure(webView) }, 300)
        webView.postDelayed({ measure(webView) }, 1_200)
    }
}

@Composable
internal fun rememberHtmlWebViewHeightDefaults(): Pair<Dp, Dp> {
    val density = LocalDensity.current.density
    return HtmlWebViewHeightMeasurer.MIN_HEIGHT_DP.dp to HtmlWebViewHeightMeasurer.DEFAULT_HEIGHT_DP.dp
}
