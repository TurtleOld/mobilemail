package com.mobilemail.ui.messages

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mobilemail.domain.model.MessageListItem
import com.mobilemail.ui.common.MonogramAvatar
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
private val dateFormat = SimpleDateFormat("d MMM", Locale.getDefault())

fun formatMessageTime(date: Date): String {
    val cal = Calendar.getInstance()
    val today = cal.get(Calendar.DAY_OF_YEAR)
    val year = cal.get(Calendar.YEAR)
    cal.time = date
    return if (cal.get(Calendar.YEAR) == year && cal.get(Calendar.DAY_OF_YEAR) == today) {
        timeFormat.format(date)
    } else {
        dateFormat.format(date)
    }
}

fun dateSectionLabel(date: Date): String {
    val cal = Calendar.getInstance()
    val todayDay = cal.get(Calendar.DAY_OF_YEAR)
    val year = cal.get(Calendar.YEAR)
    cal.time = date
    return when {
        cal.get(Calendar.YEAR) == year && cal.get(Calendar.DAY_OF_YEAR) == todayDay -> "Сегодня"
        cal.get(Calendar.YEAR) == year && cal.get(Calendar.DAY_OF_YEAR) == todayDay - 1 -> "Вчера"
        else -> SimpleDateFormat("d MMMM", Locale.getDefault()).format(date)
    }
}

@Composable
fun DateSectionHeader(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EmailListItem(
    message: MessageListItem,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val unread = message.flags.unread
    val senderName = message.from.name?.ifBlank { message.from.email } ?: message.from.email

    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.TopEnd) {
                val ring = if (unread)
                    Modifier.border(2.dp, cs.primary, CircleShape) else Modifier
                Box(ring.clip(CircleShape)) {
                    MonogramAvatar(name = senderName, size = 48.dp)
                }
                if (unread) {
                    Surface(
                        color = cs.primary,
                        shape = CircleShape,
                        border = BorderStroke(2.5.dp, cs.surface),
                        modifier = Modifier.size(14.dp),
                    ) {}
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = senderName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium,
                        color = cs.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = formatMessageTime(message.date),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (unread) cs.primary else cs.onSurfaceVariant,
                    )
                }
                Text(
                    text = message.subject,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (unread) cs.onSurface else cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = message.snippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 78.dp),
            color = cs.outlineVariant.copy(alpha = 0.5f),
        )
    }
}
