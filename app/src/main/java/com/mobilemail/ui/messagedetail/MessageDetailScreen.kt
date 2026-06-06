package com.mobilemail.ui.messagedetail

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.ReplyAll
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import com.mobilemail.ui.common.isExpandedWindowWidth
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
import com.mobilemail.ui.common.MonogramAvatar
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
    onMessageMoved: ((String) -> Unit)? = null
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
            bottomBar = {
                uiState.message?.let { message ->
                    Surface(tonalElevation = 2.dp) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilledTonalButton(
                                onClick = { onReply?.invoke(message) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Ответить")
                            }
                            OutlinedButton(
                                onClick = { onForward?.invoke(message) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Forward, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Переслать")
                            }
                        }
                    }
                }
            },
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
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
                            Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = "Переместить")
                        }
                        Box {
                            IconButton(onClick = { showReplyMenu = true }) {
                                Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = "Ответить")
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
                                        Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Ответить всем") },
                                    onClick = {
                                        showReplyMenu = false
                                        onReplyAll?.invoke(message)
                                    },
                                    leadingIcon = {
                                        Icon(Icons.AutoMirrored.Filled.ReplyAll, contentDescription = null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Переслать") },
                                    onClick = {
                                        showReplyMenu = false
                                        onForward?.invoke(message)
                                    },
                                    leadingIcon = {
                                        Icon(Icons.AutoMirrored.Filled.Forward, contentDescription = null)
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
    modifier: Modifier = Modifier,
    onDownloadAttachment: (String, String, String) -> Unit = { _, _, _ -> },
    onOpenAttachment: (String, String, String) -> Unit = { _, _, _ -> },
) {
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]
    val dateFormat = remember(locale) { SimpleDateFormat("dd.MM.yyyy HH:mm", locale) }
    val scrollState = rememberScrollState()
    val isExpandedLayout = isExpandedWindowWidth()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { contentDescription = "Содержимое письма" }
    ) {
        Text(
            text = message.subject,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Row(
            modifier = Modifier.padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MonogramAvatar(name = message.from.name ?: message.from.email, size = 40.dp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = message.from.name ?: message.from.email,
                    style = MaterialTheme.typography.titleSmall,
                )
                val toText = if (message.to.isNotEmpty()) {
                    message.to.joinToString(", ") { addr ->
                        when {
                            !addr.name.isNullOrBlank() -> addr.name.orEmpty()
                            addr.email.isNotBlank() -> addr.email
                            else -> "(без адреса)"
                        }
                    }.let { "кому: $it" }
                } else null
                Text(
                    text = listOfNotNull(toText, dateFormat.format(message.date)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        MessageExpandedContent(
            message = message,
            onDownloadAttachment = onDownloadAttachment,
            onOpenAttachment = onOpenAttachment,
            isExpandedLayout = isExpandedLayout
        )
    }
}

@Composable
private fun MessageExpandedContent(
    message: MessageDetail,
    onDownloadAttachment: (String, String, String) -> Unit,
    onOpenAttachment: (String, String, String) -> Unit,
    isExpandedLayout: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
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
    val primary = MaterialTheme.colorScheme.primary

    val annotatedString = buildAnnotatedString {
        val matcher = urlPattern.matcher(text)
        var lastIndex = 0

        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()
            var url = matcher.group()

            if (start > lastIndex) append(text.substring(lastIndex, start))

            if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://$url"
            addLink(
                url = LinkAnnotation.Url(
                    url = url,
                    styles = TextLinkStyles(
                        style = SpanStyle(color = primary, textDecoration = TextDecoration.Underline)
                    ),
                    linkInteractionListener = { annotation ->
                        try {
                            openExternalUriSafely(context, (annotation as LinkAnnotation.Url).url.toUri())
                        } catch (e: Exception) {
                            android.util.Log.e("MessageDetailScreen", "Ошибка открытия ссылки", e)
                        }
                    }
                ),
                start = length,
                end = length + matcher.group().length
            )
            withStyle(SpanStyle(color = primary, textDecoration = TextDecoration.Underline)) {
                append(matcher.group())
            }

            lastIndex = end
        }

        if (lastIndex < text.length) append(text.substring(lastIndex))
    }

    Text(text = annotatedString, style = style, modifier = modifier)
}
