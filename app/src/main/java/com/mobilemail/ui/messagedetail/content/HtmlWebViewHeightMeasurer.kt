package com.mobilemail.ui.messagedetail.content

import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal object HtmlWebViewHeightMeasurer {
    const val MIN_HEIGHT_DP = 160
    const val DEFAULT_HEIGHT_DP = 420
    const val HEIGHT_PADDING_DP = 24

    /**
     * Convert the WebView's reported content height into a Compose [Dp] for the
     * AndroidView wrapper.
     *
     * [contentHeightCssPx] is `WebView.getContentHeight()` (density-independent CSS
     * pixels); [scale] is `WebView.getScale()` (on-screen pixels per CSS pixel).
     * Their product is the actual on-screen pixel height — this is what makes the
     * result correct once shrink-to-fit (overview mode) zooms a wide email down:
     * the layout stays tall in CSS px, but [scale] shrinks below the device default,
     * so the visible box matches the scaled-down rendering instead of leaving a gap.
     */
    fun parseMeasuredHeight(contentHeightCssPx: Int, scale: Float, density: Float): Dp? {
        if (contentHeightCssPx <= 0 || scale <= 0f || density <= 0f) return null
        val onScreenPx = contentHeightCssPx * scale
        if (onScreenPx <= 0f) return null
        return (onScreenPx / density).dp
            .plus(HEIGHT_PADDING_DP.dp)
            .coerceAtLeast(MIN_HEIGHT_DP.dp)
    }

    fun scheduleHeightMeasurement(webView: WebView, onHeight: (Dp) -> Unit) {
        // WebView.getScale() is deprecated but remains the only API exposing the
        // current shrink-to-fit zoom factor we need to size the wrapper correctly.
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
