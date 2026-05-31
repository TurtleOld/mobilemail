package com.mobilemail.ui.messagedetail

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.mobilemail.domain.model.MessageDetail
import com.mobilemail.ui.messagedetail.content.MessageBodySection
import com.mobilemail.ui.messagedetail.content.openExternalUriSafely
import com.mobilemail.domain.model.MessageListItem
import com.mobilemail.ui.theme.EmailShapes
import com.mobilemail.ui.theme.EmailTypography
import com.mobilemail.ui.theme.ExtendedTheme
import java.util.regex.Pattern
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobilemail.ui.common.FeatureScreenEffects
import com.mobilemail.ui.common.rememberFeatureScreenSnackbarHostState
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageDetailScreen(
    viewModel: MessageDetailViewModel,
    onBack: () -> Unit,
    onReply: ((com.mobilemail.domain.model.MessageDetail) -> Unit)? = null,
    onReplyAll: ((com.mobilemail.domain.model.MessageDetail) -> Unit)? = null,
    onForward: ((com.mobilemail.domain.model.MessageDetail) -> Unit)? = null,
    onMessageDeleted: ((String) -> Unit)? = null,
    onReadStatusChanged: ((String, Boolean) -> Unit)? = null,
    onMessageMoved: ((String) -> Unit)? = null,
    onThreadMessageClick: ((String) -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = rememberFeatureScreenSnackbarHostState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showReplyMenu by remember { mutableStateOf(false) }

    FeatureScreenEffects(
        uiState = uiState,
        onErrorConsumed = viewModel::clearError,
        onNotificationConsumed = viewModel::clearNotification,
        snackbarHostState = snackbarHostState,
    )

    // Диалог подтверждения удаления
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Удалить письмо?") },
            text = { Text("Письмо будет удалено и вы вернетесь к списку входящих.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteMessage(onBack, onMessageDeleted)
                    }
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    if (showMoveDialog) {
        val currentMailboxIds = uiState.message?.mailboxIds.orEmpty()
        AlertDialog(
            onDismissRequest = { showMoveDialog = false },
            title = { Text("Переместить в папку") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(
                        uiState.folders.filter { it.id !in currentMailboxIds },
                        key = { it.id }
                    ) { folder ->
                        ListItem(
                            headlineContent = { Text(folder.name) },
                            modifier = Modifier.clickable {
                                viewModel.moveMessage(
                                    toFolderId = folder.id,
                                    onSuccess = onBack,
                                    onMessageRemoved = onMessageMoved
                                )
                                showMoveDialog = false
                            }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMoveDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { 
                        Text(
                            text = uiState.message?.subject ?: "Письмо",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    uiState.message?.let { message ->
                        // Кнопка звездочки
                        IconButton(onClick = { viewModel.toggleStarred() }) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = if (message.flags.starred) "Убрать из избранного" else "Добавить в избранное",
                                tint = if (message.flags.starred) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        // Кнопка пометки прочитанным
                        IconButton(onClick = { viewModel.toggleReadStatus(onReadStatusChanged) }) {
                            Icon(
                                imageVector = if (message.flags.unread) {
                                    Icons.Default.Email
                                } else {
                                    Icons.Default.MailOutline
                                },
                                contentDescription = if (message.flags.unread) "Пометить прочитанным" else "Пометить непрочитанным"
                            )
                        }
                        IconButton(onClick = {
                            viewModel.archiveMessage(
                                onSuccess = onBack,
                                onMessageRemoved = onMessageMoved
                            )
                        }) {
                            Icon(Icons.Default.Archive, contentDescription = "Архивировать")
                        }
                        IconButton(onClick = {
                            viewModel.reportSpam(
                                onSuccess = onBack,
                                onMessageRemoved = onMessageMoved
                            )
                        }) {
                            Icon(Icons.Default.Report, contentDescription = "В спам")
                        }
                        IconButton(onClick = { showMoveDialog = true }) {
                            Icon(Icons.Default.DriveFileMove, contentDescription = "Переместить")
                        }
                        Box {
                            IconButton(onClick = { showReplyMenu = true }) {
                                Icon(Icons.Default.Reply, contentDescription = "Ответить")
                            }
                            DropdownMenu(
                                expanded = showReplyMenu,
                                onDismissRequest = { showReplyMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Ответить") },
                                    onClick = {
                                        showReplyMenu = false
                                        onReply?.invoke(message)
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Reply, contentDescription = null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Ответить всем") },
                                    onClick = {
                                        showReplyMenu = false
                                        onReplyAll?.invoke(message)
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.ReplyAll, contentDescription = null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Переслать") },
                                    onClick = {
                                        showReplyMenu = false
                                        onForward?.invoke(message)
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Forward, contentDescription = null)
                                    }
                                )
                            }
                        }
                        // Кнопка удаления
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Удалить")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            uiState.message?.let { message ->
                MessageContent(
                    message = message,
                    threadMessages = uiState.threadMessages,
                    threadDetails = uiState.threadDetails,
                    onThreadMessageClick = onThreadMessageClick,
                    onDownloadAttachment = { attachmentId, filename, mimeType ->
                        viewModel.downloadAttachment(attachmentId, filename, mimeType)
                    },
                    onOpenAttachment = { attachmentId, filename, mimeType ->
                        viewModel.openAttachment(attachmentId, filename, mimeType)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            } ?: run {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Text(uiState.error?.getUserMessage() ?: "Письмо не найдено")
                }
            }
        }
    }
}

@Composable
fun MessageContent(
    message: com.mobilemail.domain.model.MessageDetail,
    threadMessages: List<MessageListItem> = emptyList(),
    threadDetails: List<MessageDetail> = emptyList(),
    onThreadMessageClick: ((String) -> Unit)? = null,
    onDownloadAttachment: (String, String, String) -> Unit = { _, _, _ -> },
    onOpenAttachment: (String, String, String) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]
    val dateFormat = remember(locale) { SimpleDateFormat("dd.MM.yyyy HH:mm", locale) }
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val isExpandedLayout = configuration.screenWidthDp >= 840
    val conversationMessages = remember(threadDetails, message) {
        if (threadDetails.isNotEmpty()) threadDetails else listOf(message)
    }
    var expandedMessageIds by remember(conversationMessages, message.id) {
        mutableStateOf(setOf(message.id))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
            .semantics { contentDescription = "Содержимое письма" }
    ) {
        Text(
            text = message.subject,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (isExpandedLayout) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                MessageEnvelope(
                    label = "От",
                    value = message.from.name ?: message.from.email,
                    modifier = Modifier.weight(1f)
                )
                if (message.to.isNotEmpty()) {
                    MessageEnvelope(
                        label = "Кому",
                        value = message.to.joinToString(", ") { addr ->
                            when {
                                !addr.name.isNullOrBlank() -> addr.name.orEmpty()
                                addr.email.isNotBlank() -> addr.email
                                else -> "(без адреса)"
                            }
                        },
                        modifier = Modifier.weight(1.2f)
                    )
                }
            }
        } else {
            MessageEnvelope(
                label = "От",
                value = message.from.name ?: message.from.email,
                modifier = Modifier.fillMaxWidth()
            )
            if (message.to.isNotEmpty()) {
                MessageEnvelope(
                    label = "Кому",
                    value = message.to.joinToString(", ") { addr ->
                        when {
                            !addr.name.isNullOrBlank() -> addr.name.orEmpty()
                            addr.email.isNotBlank() -> addr.email
                            else -> "(без адреса)"
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Text(
            text = dateFormat.format(message.date),
            style = EmailTypography.emailTimestamp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (conversationMessages.size > 1) {
            ConversationHeader(
                threadMessages = threadMessages,
                threadDetails = conversationMessages,
                currentMessageId = message.id,
                onThreadMessageClick = onThreadMessageClick,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        conversationMessages.forEach { threadMessage ->
            val isCurrent = threadMessage.id == message.id
            val isExpanded = expandedMessageIds.contains(threadMessage.id) || isCurrent
            ConversationMessageCard(
                message = threadMessage,
                isCurrent = isCurrent,
                isExpanded = isExpanded,
                onToggleExpanded = {
                    expandedMessageIds = if (expandedMessageIds.contains(threadMessage.id) && !isCurrent) {
                        expandedMessageIds - threadMessage.id
                    } else {
                        expandedMessageIds + threadMessage.id
                    }
                },
                onFocusMessage = if (!isCurrent && onThreadMessageClick != null) {
                    { onThreadMessageClick(threadMessage.id) }
                } else {
                    null
                },
                onDownloadAttachment = onDownloadAttachment,
                onOpenAttachment = onOpenAttachment,
                context = context,
                isExpandedLayout = isExpandedLayout
            )
        }
    }
}

@Composable
private fun ConversationHeader(
    threadMessages: List<MessageListItem>,
    threadDetails: List<MessageDetail>,
    currentMessageId: String,
    onThreadMessageClick: ((String) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) { SimpleDateFormat("dd.MM HH:mm", locale) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Переписка (${threadMessages.size})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            threadMessages.forEach { threadMessage ->
                val isCurrent = threadMessage.id == currentMessageId
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            role = Role.Button
                            stateDescription = if (isCurrent) "Открыто" else "Доступно для открытия"
                        }
                        .clickable(enabled = !isCurrent && onThreadMessageClick != null) {
                            onThreadMessageClick?.invoke(threadMessage.id)
                        },
                    color = if (isCurrent) {
                        ExtendedTheme.colors.threadHighlight
                    } else {
                        ExtendedTheme.colors.surfaceElevated
                    },
                    shape = EmailShapes.emailCard
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = threadMessage.from.name ?: threadMessage.from.email,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isCurrent || threadMessage.flags.unread) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = dateFormat.format(threadMessage.date),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = threadMessage.subject,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (threadMessage.snippet.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = threadMessage.snippet,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (threadDetails.any { it.id == threadMessage.id && it.attachments.isNotEmpty() }) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Есть вложения",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Suppress("UnusedParameter")
@Composable
private fun ConversationMessageCard(
    message: MessageDetail,
    isCurrent: Boolean,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onFocusMessage: (() -> Unit)?,
    onDownloadAttachment: (String, String, String) -> Unit,
    onOpenAttachment: (String, String, String) -> Unit,
    context: Context,
    isExpandedLayout: Boolean
) {
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) { SimpleDateFormat("dd.MM.yyyy HH:mm", locale) }
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = when {
                isCurrent -> ExtendedTheme.colors.threadHighlight
                isExpanded -> ExtendedTheme.colors.surfaceElevated
                else -> ExtendedTheme.colors.surfaceReading
            }
        ),
        shape = EmailShapes.emailCard
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = message.from.name ?: message.from.email,
                        style = EmailTypography.emailSenderUnread,
                        fontWeight = if (isCurrent || message.flags.unread) FontWeight.Bold else FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = recipientSummary(message),
                        style = EmailTypography.emailPreview,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (isExpanded) 3 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = dateFormat.format(message.date),
                        style = EmailTypography.emailTimestamp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isCurrent) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Текущее письмо",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message.subject,
                style = EmailTypography.emailSubject,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = onToggleExpanded,
                    label = { Text(if (isExpanded) "Свернуть" else "Развернуть") }
                )
                if (onFocusMessage != null) {
                    AssistChip(
                        onClick = onFocusMessage,
                        label = { Text("Открыть отдельно") }
                    )
                }
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider()
                Spacer(modifier = Modifier.height(12.dp))
                if (message.attachments.isNotEmpty()) {
                    Text(
                        text = "Вложения (${message.attachments.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    message.attachments.forEach { attachment ->
                        AttachmentItem(
                            attachment = attachment,
                            onDownload = { onDownloadAttachment(attachment.id, attachment.filename, attachment.mime) },
                            onOpen = { onOpenAttachment(attachment.id, attachment.filename, attachment.mime) }
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                MessageBodySection(
                    message = message,
                    isExpandedLayout = isExpandedLayout
                )
            }
        }
    }
}

private fun recipientSummary(message: MessageDetail): String {
    val recipients = buildList {
        if (message.to.isNotEmpty()) add("Кому: ${message.to.joinToString(", ") { it.name ?: it.email }}")
        if (!message.cc.isNullOrEmpty()) add("Копия: ${message.cc.joinToString(", ") { it.name ?: it.email }}")
    }
    return recipients.joinToString(" • ").ifBlank { "Без получателей" }
}

@Composable
private fun MessageEnvelope(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(bottom = 8.dp)
    ) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentItem(
    attachment: com.mobilemail.domain.model.Attachment,
    onDownload: () -> Unit,
    onOpen: () -> Unit
) {
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024L -> "$bytes Б"
            bytes < 1024L * 1024L -> "${bytes / 1024L} КБ"
            else -> "${bytes / (1024L * 1024L)} МБ"
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "Вложение ${attachment.filename}, ${formatFileSize(attachment.size)}"
            },
        onClick = onOpen,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Email,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = attachment.filename,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val sizeText = formatFileSize(attachment.size)
                val mimeText = if (attachment.mime == "application/octet-stream") "" else attachment.mime
                val metaText = if (mimeText.isEmpty()) sizeText else "$sizeText • $mimeText"
                Text(
                    text = metaText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row {
                TextButton(onClick = onOpen) {
                    Text("Открыть", style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = onDownload) {
                    Text("Скачать", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun ClickableTextWithLinks(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    context: Context
) {
    val urlPattern = Pattern.compile(
        "(?:(?:https?|ftp)://|www\\.)[\\w\\-]+(?:\\.[\\w\\-]+)+[\\w\\-.,@?^=%&:/~+#]*[\\w\\-@?^=%&/~+#]",
        Pattern.CASE_INSENSITIVE
    )
    
    val annotatedString = buildAnnotatedString {
        val matcher = urlPattern.matcher(text)
        var lastIndex = 0
        
        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()
            val url = matcher.group()
            
            if (start > lastIndex) {
                append(text.substring(lastIndex, start))
            }
            
            pushStringAnnotation(tag = "URL", annotation = url)
            withStyle(
                style = SpanStyle(
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline
                )
            ) {
                append(url)
            }
            pop()
            
            lastIndex = end
        }
        
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }
    
    ClickableText(
        text = annotatedString,
        style = style,
        modifier = modifier,
        onClick = { offset ->
            annotatedString.getStringAnnotations(
                tag = "URL",
                start = offset,
                end = offset
            ).firstOrNull()?.let { annotation ->
                try {
                    var url = annotation.item
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        url = "http://$url"
                    }
                    openExternalUriSafely(context, Uri.parse(url))
                } catch (e: Exception) {
                    android.util.Log.e("MessageDetailScreen", "Ошибка открытия ссылки: ${annotation.item}", e)
                }
            }
        }
    )
}
