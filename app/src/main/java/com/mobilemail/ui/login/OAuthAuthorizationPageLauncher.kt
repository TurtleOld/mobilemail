package com.mobilemail.ui.login

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

private const val TAG = "OAuthAuthPageLauncher"

internal data class AuthorizationPageLaunchResult(
    val opened: Boolean,
    val errorMessage: String? = null
)

internal fun openAuthorizationPage(context: Context, uri: String): AuthorizationPageLaunchResult {
    val parsed = uri.toUri()
    if (!parsed.isHttpUrl()) {
        return AuthorizationPageLaunchResult(
            opened = false,
            errorMessage = "Некорректная ссылка авторизации"
        )
    }

    return launchCustomTab(context, parsed).recoverWith {
        launchBrowserIntent(context, parsed)
    }
}

private fun launchCustomTab(context: Context, uri: Uri): AuthorizationPageLaunchResult {
    return try {
        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        if (context.findActivity() == null) {
            customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        customTabsIntent.launchUrl(context, uri)
        AuthorizationPageLaunchResult(opened = true)
    } catch (e: Exception) {
        Log.w(TAG, "Custom Tabs launch failed, falling back to ACTION_VIEW", e)
        AuthorizationPageLaunchResult(
            opened = false,
            errorMessage = "Не удалось открыть вкладку браузера"
        )
    }
}

private fun launchBrowserIntent(context: Context, uri: Uri): AuthorizationPageLaunchResult {
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        if (context.findActivity() == null) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    return try {
        context.startActivity(intent)
        AuthorizationPageLaunchResult(opened = true)
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "No browser activity found for OAuth authorization URL", e)
        AuthorizationPageLaunchResult(
            opened = false,
            errorMessage = "На устройстве не найден браузер для открытия страницы авторизации"
        )
    } catch (e: SecurityException) {
        Log.w(TAG, "Browser launch blocked by Android security policy", e)
        AuthorizationPageLaunchResult(
            opened = false,
            errorMessage = "Android заблокировал открытие страницы авторизации"
        )
    }
}

private fun AuthorizationPageLaunchResult.recoverWith(
    fallback: () -> AuthorizationPageLaunchResult
): AuthorizationPageLaunchResult {
    return if (opened) this else fallback()
}

private fun Uri.isHttpUrl(): Boolean {
    val normalizedScheme = scheme?.lowercase()
    return normalizedScheme == "https" || normalizedScheme == "http"
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
