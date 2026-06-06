package com.mobilemail.ui.messages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobilemail.domain.model.MessageListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableEmailItem(
    message: MessageListItem,
    onClick: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val state = rememberSwipeToDismissBoxState()

    LaunchedEffect(state.currentValue) {
        when (state.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> onArchive()
            SwipeToDismissBoxValue.EndToStart -> onDelete()
            SwipeToDismissBoxValue.Settled -> Unit
        }
    }

    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        backgroundContent = {
            val toEnd = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            val bg = if (toEnd) cs.secondaryContainer else cs.errorContainer
            val icon = if (toEnd) Icons.Default.Archive else Icons.Default.Delete
            val tint = if (toEnd) cs.onSecondaryContainer else cs.onErrorContainer
            val align = if (toEnd) Alignment.CenterStart else Alignment.CenterEnd

            Surface(
                color = bg,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 28.dp),
                    contentAlignment = align,
                ) {
                    Icon(icon, contentDescription = null, tint = tint)
                }
            }
        },
    ) {
        EmailListItem(
            message = message,
            onClick = onClick,
            onLongClick = onLongClick,
        )
    }
}
