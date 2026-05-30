package com.mobilemail.ui.messagedetail

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.MotionEvent
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.ui.viewinterop.AndroidView
import com.mobilemail.data.model.MessageDetail
import com.mobilemail.data.model.MessageListItem
import com.mobilemail.data.preferences.PreferencesManager
import com.mobilemail.ui.theme.EmailShapes
import com.mobilemail.ui.theme.EmailTypography
import com.mobilemail.ui.theme.ExtendedTheme
import java.util.regex.Pattern
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobilemail.ui.common.NotificationState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

private object RemoteContentAllowanceStore {
    private val allowedMessageIds = mutableSetOf<String>()

    fun isAllowed(messageId: String): Boolean = allowedMessageIds.contains(messageId)

    fun allow(messageId: String) {
        allowedMessageIds.add(messageId)
    }
}

private val disallowedHtmlTagsRegex = Regex(
    pattern = "(?is)<(script|iframe|object|embed|base|meta|link)(?:\\s[^>]*)?>.*?</\\1\\s*>|<(script|iframe|object|embed|base|meta|link)(?:\\s[^>]*)?/?>"
)

private val inlineEventHandlersRegex = Regex(pattern = "(?i)\\son[a-z]+\\s*=\\s*(['\"]).*?\\1")

private val javascriptHrefRegex = Regex(pattern = "(?i)(href|src)\\s*=\\s*(['\"])\\s*javascript:[^'\"]*\\2")

private fun sanitizeHtmlForWebView(rawHtml: String): String {
    val withoutDangerousTags = rawHtml.replace(disallowedHtmlTagsRegex, "")
    val withoutInlineHandlers = withoutDangerousTags.replace(inlineEventHandlersRegex, "")
    return withoutInlineHandlers.replace(javascriptHrefRegex, "$1=\"#\"")
}

private fun isAllowedExternalUri(uri: Uri?): Boolean {
    val scheme = uri?.scheme?.lowercase(Locale.ROOT) ?: return false
    return scheme == "https" || scheme == "http" || scheme == "mailto"
}

private fun openExternalUriSafely(context: Context, uri: Uri) {
    if (!isAllowedExternalUri(uri)) {
        android.util.Log.w("MessageDetailScreen", "Blocked unsupported uri scheme: $uri")
        return
    }
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }.onFailure { error ->
        android.util.Log.e("MessageDetailScreen", "Failed to open uri: $uri", error)
    }
}

internal fun shouldDisallowParentIntercept(actionMasked: Int): Boolean? {
    return when (actionMasked) {
        MotionEvent.ACTION_DOWN,
        MotionEvent.ACTION_MOVE -> true
        MotionEvent.ACTION_UP,
        MotionEvent.ACTION_CANCEL -> false
        else -> null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageDetailScreen(
    viewModel: MessageDetailViewModel,
    onBack: () -> Unit,
    onReply: ((com.mobilemail.data.model.MessageDetail) -> Unit)? = null,
    onReplyAll: ((com.mobilemail.data.model.MessageDetail) -> Unit)? = null,
    onForward: ((com.mobilemail.data.model.MessageDetail) -> Unit)? = null,
    onMessageDeleted: ((String) -> Unit)? = null,
    onReadStatusChanged: ((String, Boolean) -> Unit)? = null,
    onMessageMoved: ((String) -> Unit)? = null,
    onThreadMessageClick: ((String) -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showReplyMenu by remember { mutableStateOf(false) }

    // Обработка уведомлений
    LaunchedEffect(uiState.notification) {
        when (val notification = uiState.notification) {
            is NotificationState.Snackbar -> {
                scope.launch {
                    val duration: androidx.compose.material3.SnackbarDuration = when (notification.duration) {
                        com.mobilemail.ui.common.SnackbarDuration.Short -> androidx.compose.material3.SnackbarDuration.Short
                        com.mobilemail.ui.common.SnackbarDuration.Long -> androidx.compose.material3.SnackbarDuration.Long
                        com.mobilemail.ui.common.SnackbarDuration.Indefinite -> androidx.compose.material3.SnackbarDuration.Indefinite
                    }
                    val result = snackbarHostState.showSnackbar(
                        message = notification.message,
                        duration = duration,
                        actionLabel = notification.actionLabel
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        notification.onAction?.invoke()
                    }
                    viewModel.clearNotification()
                }
            }
            is NotificationState.Alert -> {
                // Alert будет обработан через AlertDialog
            }
            is NotificationState.None -> {}
        }
    }

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
                                tint = if (message.flags.starred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
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
    message: com.mobilemail.data.model.MessageDetail,
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
                    context = context,
                    isExpandedLayout = isExpandedLayout
                )
            }
        }
    }
}

@Composable
private fun MessageBodySection(
    message: MessageDetail,
    context: Context,
    isExpandedLayout: Boolean
) {
    val preferencesManager = remember(context) { PreferencesManager(context) }
    val blockRemoteContent by preferencesManager.blockRemoteContent.collectAsStateWithLifecycle(initialValue = true)
    var allowRemoteContentForMessage by remember(message.id) {
        mutableStateOf(RemoteContentAllowanceStore.isAllowed(message.id))
    }
    var webViewHeight by remember(message.id) { mutableStateOf(420.dp) }
    var isHtmlLoading by remember(message.id) { mutableStateOf(true) }

    fun composeResponsiveHtml(sourceHtml: String): String {
        val styleBlock = """
            <style>
                html, body {
                    max-width: 100% !important;
                    overflow-x: hidden !important;
                }
                body {
                    margin: 0 !important;
                    padding: 8px !important;
                    word-wrap: break-word !important;
                    overflow-wrap: anywhere !important;
                    min-width: 0 !important;
                }
                * {
                    box-sizing: border-box !important;
                    max-width: 100% !important;
                    min-width: 0 !important;
                }
                img, video, iframe {
                    max-width: 100% !important;
                    height: auto !important;
                }
                table {
                    width: 100% !important;
                    table-layout: fixed !important;
                }
                td, th, pre, code, blockquote {
                    word-break: break-word !important;
                    white-space: pre-wrap !important;
                }
                [style*="width"] {
                    max-width: 100% !important;
                }
            </style>
        """.trimIndent()
        val viewportMeta = "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes\">"

        return when {
            sourceHtml.contains("<head>", ignoreCase = true) -> {
                sourceHtml.replace(
                    "<head>",
                    "<head>$viewportMeta$styleBlock",
                    ignoreCase = true
                )
            }

            sourceHtml.contains("<html>", ignoreCase = true) -> {
                sourceHtml.replace(
                    "<html>",
                    "<html><head>$viewportMeta$styleBlock</head>",
                    ignoreCase = true
                )
            }

            else -> {
                "<html><head>$viewportMeta$styleBlock</head><body>$sourceHtml</body></html>"
            }
        }
    }

    message.body.html?.let { html ->
        val sanitizedHtml = sanitizeHtmlForWebView(html)
        val adaptedHtml = composeResponsiveHtml(sanitizedHtml)
        val contentKey = "${message.id}:${adaptedHtml.hashCode()}"
        LaunchedEffect(contentKey) {
            isHtmlLoading = true
            delay(2500)
            if (isHtmlLoading) {
                isHtmlLoading = false
            }
        }

        if (blockRemoteContent && !allowRemoteContentForMessage) {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = ExtendedTheme.colors.chromeMuted)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "Удалённый контент заблокирован",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Внешние изображения и трекеры отключены для защиты конфиденциальности.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    TextButton(
                        onClick = {
                            allowRemoteContentForMessage = true
                            RemoteContentAllowanceStore.allow(message.id)
                        },
                        modifier = Modifier.align(Alignment.End),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("Показать изображения")
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        val recalculateHeight: (WebView) -> Unit = { currentWebView ->
                            currentWebView.evaluateJavascript(
                                """
                                (function() {
                                    var body = document.body || {};
                                    var doc = document.documentElement || {};
                                    return Math.max(
                                        body.scrollHeight || 0,
                                        body.offsetHeight || 0,
                                        doc.clientHeight || 0,
                                        doc.scrollHeight || 0,
                                        doc.offsetHeight || 0
                                    ).toString();
                                })();
                                """.trimIndent()
                            ) { rawHeight ->
                                val normalized = rawHeight.trim().replace("\"", "")
                                val htmlHeight = normalized.toFloatOrNull()
                                if (htmlHeight != null && htmlHeight > 0f) {
                                    val calculatedHeight = htmlHeight.dp + 24.dp
                                    webViewHeight = calculatedHeight.coerceAtLeast(420.dp)
                                }
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                view ?: return
                                view.post {
                                    recalculateHeight(view)
                                    view.postDelayed({ recalculateHeight(view) }, 300)
                                    view.postDelayed({ recalculateHeight(view) }, 1200)
                                    isHtmlLoading = false
                                }
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val targetUri = request?.url
                                if (targetUri != null && targetUri.scheme != "data" && targetUri.scheme != "file") {
                                    openExternalUriSafely(ctx, targetUri)
                                    return true
                                }
                                return false
                            }
                        }
                        settings.apply {
                            javaScriptEnabled = false
                            domStorageEnabled = false
                            useWideViewPort = false
                            loadWithOverviewMode = true
                            builtInZoomControls = true
                            displayZoomControls = false
                            setSupportZoom(true)
                            textZoom = if (isExpandedLayout) 100 else 95
                            allowFileAccess = false
                            allowContentAccess = false
                            val blockRemoteLoads = blockRemoteContent && !allowRemoteContentForMessage
                            blockNetworkImage = blockRemoteLoads
                            blockNetworkLoads = blockRemoteLoads
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                safeBrowsingEnabled = true
                            }
                        }
                        // Allow WebView to handle vertical gestures inside Compose scroll container.
                        setOnTouchListener { view, event ->
                            shouldDisallowParentIntercept(event.actionMasked)?.let { disallow ->
                                view.parent?.requestDisallowInterceptTouchEvent(disallow)
                            }
                            false
                        }
                        isHorizontalScrollBarEnabled = false
                        isVerticalScrollBarEnabled = true
                        overScrollMode = WebView.OVER_SCROLL_NEVER
                        tag = contentKey
                        loadDataWithBaseURL(null, adaptedHtml, "text/html", "UTF-8", null)
                    }
                },
                update = { webView ->
                    val shouldBlockImages = blockRemoteContent && !allowRemoteContentForMessage
                    val wasBlockingImages = webView.settings.blockNetworkImage
                    webView.settings.blockNetworkImage = shouldBlockImages
                    webView.settings.blockNetworkLoads = shouldBlockImages
                    if (wasBlockingImages && !shouldBlockImages) {
                        webView.reload()
                    }
                    if (webView.tag != contentKey) {
                        webView.tag = contentKey
                        isHtmlLoading = true
                        webViewHeight = 420.dp
                        webView.loadDataWithBaseURL(null, adaptedHtml, "text/html", "UTF-8", null)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp)
                    .height(webViewHeight)
            )

            if (isHtmlLoading) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp),
                    color = ExtendedTheme.colors.surfaceReading,
                    shape = EmailShapes.emailCard
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Загружаем содержимое письма…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    } ?: message.body.text?.let { text ->
        ClickableTextWithLinks(
            text = text,
            style = EmailTypography.emailBody,
            modifier = Modifier.padding(vertical = 4.dp),
            context = context
        )
    } ?: run {
        Text(
            text = "Письмо не содержит текста",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
    attachment: com.mobilemail.data.model.Attachment,
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
