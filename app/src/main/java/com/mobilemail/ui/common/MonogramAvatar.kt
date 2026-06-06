package com.mobilemail.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class AvatarColor(val bg: Color, val fg: Color)

private val avatarPalette = listOf(
    AvatarColor(Color(0xFFD7E3FF), Color(0xFF1A3A6B)),
    AvatarColor(Color(0xFFFFD8E4), Color(0xFF7A2F48)),
    AvatarColor(Color(0xFFD8F0D2), Color(0xFF2C4D2A)),
    AvatarColor(Color(0xFFFFDEA6), Color(0xFF6B4A00)),
    AvatarColor(Color(0xFFE9DDFF), Color(0xFF432A78)),
    AvatarColor(Color(0xFFC8ECE6), Color(0xFF08433A)),
    AvatarColor(Color(0xFFFFDBCF), Color(0xFF7A3320)),
    AvatarColor(Color(0xFFDFE0F3), Color(0xFF33384F)),
)

private fun colorFor(seed: String): AvatarColor {
    var h = 0
    for (c in seed) h = (h * 31 + c.code) and 0x7FFFFFFF
    return avatarPalette[h % avatarPalette.size]
}

private fun monogram(name: String): String =
    name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"

@Composable
fun MonogramAvatar(
    name: String,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier,
) {
    val col = colorFor(name)
    Surface(
        color = col.bg,
        shape = CircleShape,
        modifier = modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = monogram(name),
                color = col.fg,
                fontWeight = FontWeight.SemiBold,
                fontSize = (size.value * 0.42f).sp,
            )
        }
    }
}
