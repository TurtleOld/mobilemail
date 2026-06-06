package com.mobilemail.ui.messagedetail.content

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

@Composable
internal fun HtmlMessageWebView(
    contentKey: String,
    htmlDocument: String,
    isExpandedLayout: Boolean,
    blockRemoteLoads: Boolean,
    modifier: Modifier = Modifier,
) {
    val (minHeight, defaultHeight) = rememberHtmlWebViewHeightDefaults()
    var webViewHeight by remember(contentKey) { mutableStateOf(defaultHeight) }
    var isLoading by remember(contentKey) { mutableStateOf(true) }

    LaunchedEffect(contentKey) {
        isLoading = true
        delay(2_500)
        if (isLoading) {
            isLoading = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "HTML-содержимое письма" }
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            val webView = view ?: return
                            HtmlWebViewHeightMeasurer.scheduleHeightMeasurement(webView) { measured ->
                                webViewHeight = measured
                            }
                            isLoading = false
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val targetUri = request?.url ?: return true
                            // Allow only safe schemes; everything else (data:, file:,
                            // intent:, javascript:, market:, …) is blocked here.
                            // isAllowedExternalUri permits https/http/mailto only.
                            if (isAllowedExternalUri(targetUri)) {
                                openExternalUriSafely(ctx, targetUri)
                            }
                            return true
                        }
                    }
                    HtmlMessageWebViewPolicy.applySettings(
                        webView = this,
                        isExpandedLayout = isExpandedLayout,
                        blockRemoteLoads = blockRemoteLoads
                    )
                    tag = contentKey
                    loadDataWithBaseURL(null, htmlDocument, "text/html", "UTF-8", null)
                }
            },
            update = { webView ->
                HtmlMessageWebViewPolicy.updateRemoteContentBlocking(
                    webView = webView,
                    blockRemoteLoads = blockRemoteLoads,
                    reloadIfUnblocked = true
                )
                if (webView.tag != contentKey) {
                    webView.tag = contentKey
                    isLoading = true
                    webViewHeight = defaultHeight
                    webView.loadDataWithBaseURL(null, htmlDocument, "text/html", "UTF-8", null)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .height(webViewHeight)
        )

        if (isLoading) {
            HtmlLoadingPlaceholder(minHeight = minHeight)
        }
    }
}

@Composable
private fun HtmlLoadingPlaceholder(minHeight: Dp) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight.coerceAtLeast(220.dp)),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Загружаем содержимое письма…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
