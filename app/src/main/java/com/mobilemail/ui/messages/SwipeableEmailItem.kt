package com.mobilemail.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MarkEmailRead
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mobilemail.data.preferences.SwipeAction
import com.mobilemail.domain.model.MessageListItem

private fun SwipeAction.icon(): ImageVector = when (this) {
    SwipeAction.ARCHIVE   -> Icons.Default.Archive
    SwipeAction.DELETE    -> Icons.Default.Delete
    SwipeAction.MARK_READ -> Icons.Default.MarkEmailRead
    SwipeAction.NONE      -> Icons.Default.Archive
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableEmailItem(
    message: MessageListItem,
    onClick: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onMarkRead: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    swipeRightAction: SwipeAction = SwipeAction.ARCHIVE,
    swipeLeftAction: SwipeAction = SwipeAction.DELETE,
) {
    val cs = MaterialTheme.colorScheme

    fun dispatchAction(action: SwipeAction) = when (action) {
        SwipeAction.ARCHIVE   -> onArchive()
        SwipeAction.DELETE    -> onDelete()
        SwipeAction.MARK_READ -> onMarkRead()
        SwipeAction.NONE      -> Unit
    }

    val enableRight = swipeRightAction != SwipeAction.NONE
    val enableLeft  = swipeLeftAction  != SwipeAction.NONE

    val state = rememberSwipeToDismissBoxState()

    LaunchedEffect(state.currentValue) {
        when (state.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> if (enableRight) dispatchAction(swipeRightAction)
            SwipeToDismissBoxValue.EndToStart -> if (enableLeft)  dispatchAction(swipeLeftAction)
            SwipeToDismissBoxValue.Settled    -> Unit
        }
    }

    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = enableRight,
        enableDismissFromEndToStart = enableLeft,
        backgroundContent = {
            val direction = state.dismissDirection ?: return@SwipeToDismissBox
            val toEnd   = direction == SwipeToDismissBoxValue.StartToEnd
            val action  = if (toEnd) swipeRightAction else swipeLeftAction
            if (action == SwipeAction.NONE) return@SwipeToDismissBox

            val bg   = if (toEnd) cs.secondaryContainer else cs.errorContainer
            val tint = if (toEnd) cs.onSecondaryContainer else cs.onErrorContainer
            val align = if (toEnd) Alignment.CenterStart else Alignment.CenterEnd

            Surface(color = bg, modifier = Modifier.fillMaxSize()) {
                Box(
                    Modifier.fillMaxSize().padding(horizontal = 28.dp),
                    contentAlignment = align,
                ) {
                    Icon(action.icon(), contentDescription = null, tint = tint)
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
