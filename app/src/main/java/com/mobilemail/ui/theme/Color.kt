package com.mobilemail.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────
// MobileMail Color System — Material 3 Expressive
// Фирменная палитра (бирюза) — fallback при выключенном Dynamic Color.
// ─────────────────────────────────────────────────────────────

// Light scheme
private val md_primary_l              = Color(0xFF006A60)
private val md_onPrimary_l            = Color(0xFFFFFFFF)
private val md_primaryContainer_l     = Color(0xFF74F8E4)
private val md_onPrimaryContainer_l   = Color(0xFF00201C)
private val md_secondaryContainer_l   = Color(0xFFCFE8E1)
private val md_onSecondaryContainer_l = Color(0xFF08201C)
private val md_surface_l              = Color(0xFFFBF9F9)
private val md_onSurface_l            = Color(0xFF1B1B1B)
private val md_surfaceVariant_l       = Color(0xFFDAE5E1)
private val md_onSurfaceVariant_l     = Color(0xFF444746)
private val md_outline_l              = Color(0xFF74777C)
private val md_outlineVariant_l       = Color(0xFFC5C6CB)

// Dark scheme
private val md_primary_d              = Color(0xFF53DBC9)
private val md_onPrimary_d            = Color(0xFF00382F)
private val md_primaryContainer_d     = Color(0xFF005047)
private val md_onPrimaryContainer_d   = Color(0xFF74F8E4)
private val md_secondaryContainer_d   = Color(0xFF324B46)
private val md_onSecondaryContainer_d = Color(0xFFCFE8E1)
private val md_surface_d              = Color(0xFF131316)
private val md_onSurface_d            = Color(0xFFE4E2E6)
private val md_surfaceVariant_d       = Color(0xFF3F4946)
private val md_onSurfaceVariant_d     = Color(0xFFC5C6CB)
private val md_outline_d              = Color(0xFF8F9195)
private val md_outlineVariant_d       = Color(0xFF44474B)

val LightColorScheme = lightColorScheme(
    primary = md_primary_l,
    onPrimary = md_onPrimary_l,
    primaryContainer = md_primaryContainer_l,
    onPrimaryContainer = md_onPrimaryContainer_l,
    secondaryContainer = md_secondaryContainer_l,
    onSecondaryContainer = md_onSecondaryContainer_l,
    surface = md_surface_l,
    onSurface = md_onSurface_l,
    surfaceVariant = md_surfaceVariant_l,
    onSurfaceVariant = md_onSurfaceVariant_l,
    outline = md_outline_l,
    outlineVariant = md_outlineVariant_l,
)

val DarkColorScheme = darkColorScheme(
    primary = md_primary_d,
    onPrimary = md_onPrimary_d,
    primaryContainer = md_primaryContainer_d,
    onPrimaryContainer = md_onPrimaryContainer_d,
    secondaryContainer = md_secondaryContainer_d,
    onSecondaryContainer = md_onSecondaryContainer_d,
    surface = md_surface_d,
    onSurface = md_onSurface_d,
    surfaceVariant = md_surfaceVariant_d,
    onSurfaceVariant = md_onSurfaceVariant_d,
    outline = md_outline_d,
    outlineVariant = md_outlineVariant_d,
)

// ─── 8 детерминированных пар цветов для MonogramAvatar ───────
data class AvatarColorPair(val background: Color, val foreground: Color)

val avatarColors = listOf(
    AvatarColorPair(Color(0xFFD7E3FF), Color(0xFF1A3A6B)),
    AvatarColorPair(Color(0xFFFFD8E4), Color(0xFF7A2F48)),
    AvatarColorPair(Color(0xFFD8F0D2), Color(0xFF2C4D2A)),
    AvatarColorPair(Color(0xFFFFDEA6), Color(0xFF6B4A00)),
    AvatarColorPair(Color(0xFFE9DDFF), Color(0xFF432A78)),
    AvatarColorPair(Color(0xFFC8ECE6), Color(0xFF08433A)),
    AvatarColorPair(Color(0xFFFFDBCF), Color(0xFF7A3320)),
    AvatarColorPair(Color(0xFFDFE0F3), Color(0xFF33384F)),
)

// ─── Семантические цвета для email-состояний ─────────────────
val StarredColor  = Color(0xFFFFB800)

// Нейтральные тона (используются в ExtendedColors bridge)
val Neutral6  = Color(0xFF121314)
val Neutral10 = Color(0xFF1A1C1E)
val Neutral12 = Color(0xFF1E2022)
val Neutral17 = Color(0xFF282A2D)
val Neutral20 = Color(0xFF2E3134)
val Neutral22 = Color(0xFF333639)
val Neutral90 = Color(0xFFE1E2E4)
val Neutral94 = Color(0xFFEDEEF0)
val Neutral95 = Color(0xFFF0F1F3)
val Neutral96 = Color(0xFFF3F4F6)
val Neutral98 = Color(0xFFF8F6F1)
val Neutral99 = Color(0xFFFCFAF6)
val Neutral100 = Color(0xFFFFFFFF)
val Neutral0   = Color(0xFF000000)

val NeutralVariant20 = Color(0xFF2C3233)
val NeutralVariant30 = Color(0xFF424849)
val NeutralVariant80 = Color(0xFFC2C8C9)
val NeutralVariant95 = Color(0xFFEFEFE9)

// Error
val Error10 = Color(0xFF410002)
val Error20 = Color(0xFF690005)
val Error30 = Color(0xFF93000A)
val Error40 = Color(0xFFBA1A1A)
val Error50 = Color(0xFFDE3730)
val Error70 = Color(0xFFFF897D)
val Error80 = Color(0xFFFFB4AB)
val Error90 = Color(0xFFFFDAD6)

// Success
val Success10 = Color(0xFF002106)
val Success20 = Color(0xFF00390E)
val Success30 = Color(0xFF005317)
val Success40 = Color(0xFF006E22)
val Success50 = Color(0xFF008A2D)
val Success60 = Color(0xFF26A641)
val Success70 = Color(0xFF4FC35B)
val Success80 = Color(0xFF6FE074)
val Success90 = Color(0xFFB7F5B1)

// Warning
val Warning10 = Color(0xFF261A00)
val Warning20 = Color(0xFF402D00)
val Warning30 = Color(0xFF5C4200)
val Warning40 = Color(0xFF7A5800)
val Warning50 = Color(0xFF996F00)
val Warning60 = Color(0xFFB98700)
val Warning70 = Color(0xFFD9A000)
val Warning80 = Color(0xFFFABA00)
val Warning90 = Color(0xFFFFDF9E)

// Semantic (используются в ExtendedColors bridge и на экранах)
val UnreadBadge   = md_primary_l
val AttachmentIcon = md_primary_l
val DraftColor    = Warning60
val SentColor     = Success60
val SpamColor     = Error40

val Teal20 = Color(0xFF1D3B39)
val Teal95 = Color(0xFFE8F7F3)
val Blue20 = Color(0xFF203243)
val Blue40 = Color(0xFF45627A)
val Blue50 = Color(0xFF597C97)
val Blue70 = Color(0xFF90B2CD)
val Blue80 = Color(0xFFB1CEE7)
val Blue90 = Color(0xFFD3E7F8)
val Blue95 = Color(0xFFE9F2FA)
