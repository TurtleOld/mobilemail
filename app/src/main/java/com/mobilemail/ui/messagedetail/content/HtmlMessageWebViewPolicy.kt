package com.mobilemail.ui.messagedetail.content

import android.graphics.Color
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

internal object HtmlMessageWebViewPolicy {
    private const val DARK_CONTENT_BACKGROUND = 0xFF121212.toInt()

    fun applySettings(
        webView: WebView,
        isExpandedLayout: Boolean,
        blockRemoteLoads: Boolean,
        isDarkTheme: Boolean,
    ) {
        webView.settings.apply {
            javaScriptEnabled = false
            domStorageEnabled = false
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            textZoom = if (isExpandedLayout) 100 else 95
            allowFileAccess = false
            allowContentAccess = false
            val remoteContentSettings = HtmlMessageRemoteContentPolicy.settingsFor(blockRemoteLoads)
            blockNetworkImage = remoteContentSettings.blockNetworkImages
            blockNetworkLoads = remoteContentSettings.blockNetworkLoads
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled = true
        }
        webView.isHorizontalScrollBarEnabled = false
        webView.isVerticalScrollBarEnabled = false
        webView.overScrollMode = WebView.OVER_SCROLL_NEVER
        webView.applyParentScrollHandoff()
        applyDarkContentSupport(webView, isDarkTheme)
    }

    fun applyDarkContentSupport(webView: WebView, isDarkTheme: Boolean) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, isDarkTheme)
        }
        webView.setBackgroundColor(if (isDarkTheme) DARK_CONTENT_BACKGROUND else Color.WHITE)
    }

    fun updateRemoteContentBlocking(webView: WebView, blockRemoteLoads: Boolean, reloadIfUnblocked: Boolean) {
        val wasBlocking = webView.settings.blockNetworkImage
        val remoteContentSettings = HtmlMessageRemoteContentPolicy.settingsFor(blockRemoteLoads)
        webView.settings.blockNetworkImage = remoteContentSettings.blockNetworkImages
        webView.settings.blockNetworkLoads = remoteContentSettings.blockNetworkLoads
        if (wasBlocking && !blockRemoteLoads && reloadIfUnblocked) {
            webView.reload()
        }
    }
}
