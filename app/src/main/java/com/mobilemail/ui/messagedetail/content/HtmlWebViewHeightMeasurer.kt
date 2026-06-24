package com.mobilemail.ui.messagedetail.content

import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal object HtmlWebViewHeightMeasurer {
    const val MIN_HEIGHT_DP = 160
    const val DEFAULT_HEIGHT_DP = 420
    const val HEIGHT_PADDING_DP = 24

    fun parseMeasuredHeight(contentHeightCssPx: Int, scale: Float, density: Float): Dp? {
        if (contentHeightCssPx <= 0 || scale <= 0f || density <= 0f) return null
        val onScreenPx = contentHeightCssPx * scale
        if (onScreenPx <= 0f) return null
        return (onScreenPx / density).dp
            .plus(HEIGHT_PADDING_DP.dp)
            .coerceAtLeast(MIN_HEIGHT_DP.dp)
    }

    fun scheduleHeightMeasurement(webView: WebView, onHeight: (Dp) -> Unit) {
        @Suppress("DEPRECATION")
        val measure: (WebView) -> Unit = { currentWebView ->
            parseMeasuredHeight(
                contentHeightCssPx = currentWebView.contentHeight,
                scale = currentWebView.scale,
                density = currentWebView.resources.displayMetrics.density
            )?.let(onHeight)
        }
        measure(webView)
        webView.post { measure(webView) }
        webView.postDelayed({ measure(webView) }, 300)
        webView.postDelayed({ measure(webView) }, 1_200)
    }
}

@Composable
internal fun rememberHtmlWebViewHeightDefaults(): Pair<Dp, Dp> {
    return HtmlWebViewHeightMeasurer.MIN_HEIGHT_DP.dp to HtmlWebViewHeightMeasurer.DEFAULT_HEIGHT_DP.dp
}
