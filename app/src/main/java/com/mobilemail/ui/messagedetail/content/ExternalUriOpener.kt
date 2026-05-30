package com.mobilemail.ui.messagedetail.content

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.util.Locale

private const val TAG = "ExternalUriOpener"

internal fun isAllowedExternalUri(uri: Uri?): Boolean {
    val scheme = uri?.scheme?.lowercase(Locale.ROOT) ?: return false
    return scheme == "https" || scheme == "http" || scheme == "mailto"
}

internal fun openExternalUriSafely(context: Context, uri: Uri) {
    if (!isAllowedExternalUri(uri)) {
        Log.w(TAG, "Blocked unsupported uri scheme: $uri")
        return
    }
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }.onFailure { error ->
        Log.e(TAG, "Failed to open uri: $uri", error)
    }
}
