package com.mobilemail.ui.messagedetail.content

import android.webkit.WebSettings
import android.webkit.WebView

internal object HtmlMessageWebViewPolicy {
    fun applySettings(
        webView: WebView,
        isExpandedLayout: Boolean,
        blockRemoteLoads: Boolean,
    ) {
        webView.settings.apply {
            javaScriptEnabled = false
            domStorageEnabled = false
            useWideViewPort = false
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            textZoom = if (isExpandedLayout) 100 else 95
            allowFileAccess = false
            allowContentAccess = false
            blockNetworkImage = blockRemoteLoads
            blockNetworkLoads = blockRemoteLoads
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled = true
        }
        webView.isHorizontalScrollBarEnabled = false
        webView.isVerticalScrollBarEnabled = false
        webView.overScrollMode = WebView.OVER_SCROLL_NEVER
        webView.applyParentScrollHandoff()
    }

    fun updateRemoteContentBlocking(webView: WebView, blockRemoteLoads: Boolean, reloadIfUnblocked: Boolean) {
        val wasBlocking = webView.settings.blockNetworkImage
        webView.settings.blockNetworkImage = blockRemoteLoads
        webView.settings.blockNetworkLoads = blockRemoteLoads
        if (wasBlocking && !blockRemoteLoads && reloadIfUnblocked) {
            webView.reload()
        }
    }
}
