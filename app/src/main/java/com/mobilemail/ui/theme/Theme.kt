package com.mobilemail.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * MobileMail Theme 2026
 *
 * Modern design system with:
 * - Dynamic color support (Android 12+)
 * - Comfortable dark/light themes
 * - Enhanced accessibility
 * - OLED-optimized dark mode
 */

// Light Color Scheme - Calm, professional, easy on eyes
private val LightColorScheme = lightColorScheme(
    // Primary - Blue for main actions
    primary = Blue40,
    onPrimary = Color.White,
    primaryContainer = Blue90,
    onPrimaryContainer = Blue10,

    // Secondary - Teal for accents
    secondary = Teal40,
    onSecondary = Color.White,
    secondaryContainer = Teal90,
    onSecondaryContainer = Teal10,

    // Tertiary - Violet for highlights
    tertiary = Violet40,
    onTertiary = Color.White,
    tertiaryContainer = Violet90,
    onTertiaryContainer = Violet10,

    // Error states
    error = Error40,
    onError = Color.White,
    errorContainer = Error90,
    onErrorContainer = Error10,

    // Background - Slightly warm white for reduced eye strain
    background = Neutral98,
    onBackground = Neutral10,

    // Surface hierarchy
    surface = Neutral99,
    onSurface = Neutral10,
    surfaceVariant = NeutralVariant90,
    onSurfaceVariant = NeutralVariant30,

    // Outline and inverse
    outline = NeutralVariant50,
    outlineVariant = NeutralVariant80,
    inverseSurface = Neutral20,
    inverseOnSurface = Neutral95,
    inversePrimary = Blue80,

    // Surface tint and scrim
    surfaceTint = Blue40,
    scrim = Neutral0
)

// Dark Color Scheme - OLED-friendly, reduced eye strain
private val DarkColorScheme = darkColorScheme(
    // Primary - Lighter blue for visibility
    primary = Blue80,
    onPrimary = Blue20,
    primaryContainer = Blue30,
    onPrimaryContainer = Blue90,

    // Secondary - Lighter teal
    secondary = Teal80,
    onSecondary = Teal20,
    secondaryContainer = Teal30,
    onSecondaryContainer = Teal90,

    // Tertiary - Lighter violet
    tertiary = Violet80,
    onTertiary = Violet20,
    tertiaryContainer = Violet30,
    onTertiaryContainer = Violet90,

    // Error states
    error = Error80,
    onError = Error20,
    errorContainer = Error30,
    onErrorContainer = Error90,

    // Background - True dark for OLED
    background = Neutral6,
    onBackground = Neutral90,

    // Surface hierarchy - slightly elevated from background
    surface = Neutral6,
    onSurface = Neutral90,
    surfaceVariant = NeutralVariant30,
    onSurfaceVariant = NeutralVariant80,

    // Outline and inverse
    outline = NeutralVariant60,
    outlineVariant = NeutralVariant30,
    inverseSurface = Neutral90,
    inverseOnSurface = Neutral20,
    inversePrimary = Blue40,

    // Surface tint and scrim
    surfaceTint = Blue80,
    scrim = Neutral0
)

/**
 * Extended color scheme for email-specific colors
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
    val unreadBadge: Color,
    val starred: Color,
    val attachment: Color,
    val draft: Color,
    val sent: Color,
    val spam: Color,
    val surfaceElevated: Color,
    val surfaceHighest: Color,
    val surfaceCanvas: Color,
    val surfaceReading: Color,
    val chromeMuted: Color,
    val threadHighlight: Color,
    val selectionHighlight: Color
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
    unreadBadge = Blue50,
    starred = StarredColor,
    attachment = Teal50,
    draft = Warning50,
    sent = Success50,
    spam = Error50,
    surfaceElevated = Neutral96,
    surfaceHighest = Neutral94,
    surfaceCanvas = Neutral98,
    surfaceReading = Neutral100,
    chromeMuted = NeutralVariant95,
    threadHighlight = Teal95,
    selectionHighlight = Blue95
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
    unreadBadge = Blue70,
    starred = StarredColor,
    attachment = Teal70,
    draft = Warning70,
    sent = Success70,
    spam = Error70,
    surfaceElevated = Neutral17,
    surfaceHighest = Neutral22,
    surfaceCanvas = Neutral10,
    surfaceReading = Neutral12,
    chromeMuted = NeutralVariant20,
    threadHighlight = Teal20,
    selectionHighlight = Blue20
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }

/**
 * Access extended colors from MaterialTheme
 */
object ExtendedTheme {
    val colors: ExtendedColors
        @Composable
        get() = LocalExtendedColors.current
}

/**
 * Main theme composable for MobileMail
 *
 * @param darkTheme Whether to use dark theme. Defaults to system setting.
 * @param dynamicColor Whether to use dynamic color (Android 12+). Defaults to true.
 * @param content The content to be themed.
 */
@Composable
fun MobileMailTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Use dynamic colors on Android 12+ if enabled
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    // Configure status bar and navigation bar
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window

            // Transparent status bar with surface color for modern look
            window.statusBarColor = Color.Transparent.toArgb()

            // Enable edge-to-edge
            WindowCompat.setDecorFitsSystemWindows(window, false)

            // Set status bar icons based on theme
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme

            // Navigation bar color
            window.navigationBarColor = colorScheme.surface.toArgb()
        }
    }

    CompositionLocalProvider(
        LocalExtendedColors provides extendedColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}

/**
 * Preview theme wrapper for Compose previews
 */
@Composable
fun MobileMailPreviewTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MobileMailTheme(
        darkTheme = darkTheme,
        dynamicColor = false, // Disable dynamic color in previews for consistency
        content = content
    )
}
