package com.mobilemail.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Material 3 Expressive — увеличенные радиусы скруглений
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small      = RoundedCornerShape(16.dp),
    medium     = RoundedCornerShape(20.dp),
    large      = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

object Spacing {
    val none     = 0.dp
    val xxs      = 2.dp
    val xs       = 4.dp
    val sm       = 8.dp
    val md       = 12.dp
    val lg       = 16.dp
    val xl       = 20.dp
    val xxl      = 24.dp
    val xxxl     = 32.dp
    val xxxxl    = 40.dp
    val xxxxxl   = 48.dp

    val screenHorizontal  = 16.dp
    val screenVertical    = 16.dp
    val cardPadding       = 16.dp
    val cardPaddingSmall  = 12.dp
    val listItemVertical  = 12.dp
    val listItemHorizontal = 16.dp

    val iconSmall   = 16.dp
    val iconMedium  = 24.dp
    val iconLarge   = 32.dp
    val iconXLarge  = 48.dp

    val avatarSmall  = 32.dp
    val avatarMedium = 40.dp
    val avatarLarge  = 56.dp

    val buttonHeight      = 48.dp
    val buttonHeightSmall = 36.dp
    val divider           = 1.dp

    val elevationNone    = 0.dp
    val elevationLow     = 1.dp
    val elevationMedium  = 3.dp
    val elevationHigh    = 6.dp
    val elevationHighest = 8.dp
}
