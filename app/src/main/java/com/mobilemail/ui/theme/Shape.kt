package com.mobilemail.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * MobileMail Shape System 2026
 *
 * Modern, softer shapes with generous corner radii.
 * Follows the 2025-2026 design trend of "softer UI" with
 * increased corner radius values compared to earlier Material Design.
 */

val AppShapes = Shapes(
    // Extra Small - for small chips, badges, indicators
    extraSmall = RoundedCornerShape(8.dp),

    // Small - for buttons, text fields, small cards
    small = RoundedCornerShape(12.dp),

    // Medium - for cards, dialogs, containers
    medium = RoundedCornerShape(16.dp),

    // Large - for sheets, large cards, modal surfaces
    large = RoundedCornerShape(24.dp),

    // Extra Large - for full-screen sheets, navigation drawers
    extraLarge = RoundedCornerShape(32.dp)
)

/**
 * Custom shapes for specific email app components
 */
object EmailShapes {
    // Email item card - subtle rounding
    val emailCard = RoundedCornerShape(12.dp)

    // Attachment chip
    val attachmentChip = RoundedCornerShape(8.dp)

    // Avatar/Profile image - circular
    val avatar = RoundedCornerShape(50)

    // Folder item in drawer
    val folderItem = RoundedCornerShape(
        topStart = 0.dp,
        bottomStart = 0.dp,
        topEnd = 24.dp,
        bottomEnd = 24.dp
    )

    // Search bar - pill shape
    val searchBar = RoundedCornerShape(28.dp)

    // Floating action button
    val fab = RoundedCornerShape(16.dp)

    // Bottom sheet
    val bottomSheet = RoundedCornerShape(
        topStart = 28.dp,
        topEnd = 28.dp,
        bottomStart = 0.dp,
        bottomEnd = 0.dp
    )

    // Modal dialog
    val dialog = RoundedCornerShape(28.dp)

    // Snackbar
    val snackbar = RoundedCornerShape(12.dp)

    // PIN keypad button - circular
    val keypadButton = RoundedCornerShape(50)

    // PIN dot indicator
    val pinDot = RoundedCornerShape(50)

    // Settings card/section
    val settingsCard = RoundedCornerShape(16.dp)

    // Filter chip
    val filterChip = RoundedCornerShape(8.dp)

    // Badge (unread count)
    val badge = RoundedCornerShape(12.dp)

    // Text input field
    val textField = RoundedCornerShape(12.dp)

    // Top app bar (when elevated/scrolled)
    val topAppBar = RoundedCornerShape(
        topStart = 0.dp,
        topEnd = 0.dp,
        bottomStart = 0.dp,
        bottomEnd = 0.dp
    )
}

/**
 * Spacing and sizing constants for consistent layout
 */
object Spacing {
    // Base unit: 4dp grid
    val none = 0.dp
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val xxxxl = 40.dp
    val xxxxxl = 48.dp

    // Screen edge padding
    val screenHorizontal = 16.dp
    val screenVertical = 16.dp

    // Card internal padding
    val cardPadding = 16.dp
    val cardPaddingSmall = 12.dp

    // List item spacing
    val listItemVertical = 12.dp
    val listItemHorizontal = 16.dp

    // Icon sizes
    val iconSmall = 16.dp
    val iconMedium = 24.dp
    val iconLarge = 32.dp
    val iconXLarge = 48.dp

    // Avatar sizes
    val avatarSmall = 32.dp
    val avatarMedium = 40.dp
    val avatarLarge = 56.dp

    // Button heights
    val buttonHeight = 48.dp
    val buttonHeightSmall = 36.dp

    // Divider thickness
    val divider = 1.dp

    // Elevation
    val elevationNone = 0.dp
    val elevationLow = 1.dp
    val elevationMedium = 3.dp
    val elevationHigh = 6.dp
    val elevationHighest = 8.dp
}
