package com.mobilemail.ui.messagedetail

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.util.regex.Pattern
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobilemail.ui.common.NotificationState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageDetailScreen(
    viewModel: MessageDetailViewModel,
    onBack: () -> Unit,
    onMessageDeleted: ((String) -> Unit)? = null,
    onReadStatusChanged: ((String, Boolean) -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }

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
                    snackbarHostState.showSnackbar(
                        message = notification.message,
                        duration = duration,
                        actionLabel = notification.actionLabel
                    )
                    notification.onAction?.invoke()
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
            text = { Text("Письмо будет удалено без возможности восстановления.") },
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
    message: com.mobilemail.data.model.MessageDetail,
    onDownloadAttachment: (String, String, String) -> Unit = { _, _, _ -> },
    onOpenAttachment: (String, String, String) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = message.subject,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Text(
                text = "От: ",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = message.from.name ?: message.from.email,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (message.to.isNotEmpty()) {
            LaunchedEffect(message.to) {
                android.util.Log.d("MessageDetailScreen", "Отображение поля 'Кому': size=${message.to.size}")
            }
            
            Row(
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text(
                    text = "Кому: ",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = message.to.joinToString(", ") { addr ->
                        when {
                            !addr.name.isNullOrBlank() -> addr.name.orEmpty()
                            addr.email.isNotBlank() -> addr.email
                            else -> "(без адреса)"
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Text(
            text = dateFormat.format(message.date),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // Отображение вложений
        if (message.attachments.isNotEmpty()) {
            Text(
                text = "Вложения (${message.attachments.size}):",
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
            Spacer(modifier = Modifier.height(16.dp))
        }

        message.body.html?.let { html ->
            val adaptedHtml = if (html.contains("<head>", ignoreCase = true)) {
                html.replace(
                    "<head>",
                    "<head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes\">",
                    ignoreCase = true
                )
            } else if (html.contains("<html>", ignoreCase = true)) {
                html.replace(
                    "<html>",
                    "<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes\"></head>",
                    ignoreCase = true
                )
            } else {
                "<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes\"><style>body { margin: 0; padding: 8px; word-wrap: break-word; } img { max-width: 100%; height: auto; }</style></head><body>$html</body></html>"
            }
            
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val url = request?.url?.toString()
                                if (url != null && !url.startsWith("data:") && !url.startsWith("file://")) {
                                    // Открываем ссылку в браузере
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        ctx.startActivity(intent)
                                    } catch (e: Exception) {
                                        android.util.Log.e("MessageDetailScreen", "Ошибка открытия ссылки: $url", e)
                                    }
                                    return true
                                }
                                return false
                            }
                        }
                        settings.apply {
                            javaScriptEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            builtInZoomControls = true
                            displayZoomControls = false
                            setSupportZoom(true)
                            textZoom = 100
                        }
                        loadDataWithBaseURL(null, adaptedHtml, "text/html", "UTF-8", null)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 2000.dp)
            )
        } ?: message.body.text?.let { text ->
            ClickableTextWithLinks(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(vertical = 8.dp),
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
            .padding(vertical = 4.dp),
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
                    if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("ftp://")) {
                        url = "http://$url"
                    }
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                } catch (e: Exception) {
                    android.util.Log.e("MessageDetailScreen", "Ошибка открытия ссылки: ${annotation.item}", e)
                }
            }
        }
    )
}
