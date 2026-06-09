package com.mobilemail.ui.messagedetail.content

import android.webkit.WebView

internal object HtmlMessageWebViewLoader {
    internal val baseUrl: String? = null

    private const val MIME_TYPE = "text/html"
    private const val ENCODING = "UTF-8"

    fun load(webView: WebView, htmlDocument: String) {
        // Keep message HTML in an opaque origin so relative resources cannot
        // resolve against a mailbox, file, or app-owned base URL.
        webView.loadDataWithBaseURL(baseUrl, htmlDocument, MIME_TYPE, ENCODING, null)
    }
}
