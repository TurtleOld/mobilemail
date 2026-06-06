package com.mobilemail.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * MobileMail Theme — Material 3 + Expressive-стиль
 *
 * minSdk = 31 → dynamicColorScheme() доступен всегда,
 * проверка Build.VERSION не нужна.
 * [dynamicColor] — пользовательская настройка (обои ↔ фирменный бирюзовый).
 */

data class ExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val starred: Color,
    val draft: Color,
    val sent: Color,
    val spam: Color,
)

private val LightExtendedColors = ExtendedColors(
    success = Success40,
    onSuccess = Color.White,
    successContainer = Success90,
    onSuccessContainer = Success10,
    warning = Warning40,
    onWarning = Color.White,
    warningContainer = Warning90,
    onWarningContainer = Warning10,
    starred = StarredColor,
    draft = Warning50,
    sent = Success50,
    spam = Error50,
)

private val DarkExtendedColors = ExtendedColors(
    success = Success80,
    onSuccess = Success20,
    successContainer = Success30,
    onSuccessContainer = Success90,
    warning = Warning80,
    onWarning = Warning20,
    warningContainer = Warning30,
    onWarningContainer = Warning90,
    starred = StarredColor,
    draft = Warning70,
    sent = Success70,
    spam = Error70,
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }

object ExtendedTheme {
    val colors: ExtendedColors
        @Composable
        get() = LocalExtendedColors.current
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MobileMailTheme(  // @OptIn требуется для MaterialExpressiveTheme в 1.5.x alpha
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor ->
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalExtendedColors provides extendedColors
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

@Composable
fun MobileMailPreviewTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MobileMailTheme(
        darkTheme = darkTheme,
        dynamicColor = false,
        content = content
    )
}
