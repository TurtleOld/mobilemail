package com.mobilemail.ui.newmessage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.util.Log
import com.mobilemail.data.preferences.PreferencesManager
import com.mobilemail.ui.common.NotificationState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.width
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.rememberUpdatedState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewMessageScreen(
    viewModel: ComposeViewModel,
    server: String,
    email: String,
    password: String,
    accountId: String,
    onBack: () -> Unit
) {
    Log.d("NewMessageScreen", "Init: server=$server, email=$email, accountId=$accountId, passwordPlaceholder=${password == "-"}")
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var to by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    val context = LocalContext.current
    val preferencesManager = remember(context) { PreferencesManager(context) }
    val attachmentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri ->
            viewModel.addAttachment(context.contentResolver, uri)
        }
    }

    val latestTo by rememberUpdatedState(to)
    val latestSubject by rememberUpdatedState(subject)
    val latestBody by rememberUpdatedState(body)

    LaunchedEffect(server, email) {
        val signature = preferencesManager.getSignature(server, email).orEmpty()
        if (signature.isNotBlank() && body.isBlank()) {
            body = "\n\n$signature"
        }
    }

    LaunchedEffect(uiState.notification) {
        when (val notification = uiState.notification) {
            is NotificationState.Snackbar -> {
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
            is NotificationState.Alert -> {
                // Alert dialog пока не используется
            }
            is NotificationState.None -> {}
        }
    }

    LaunchedEffect(to, subject, body, uiState.attachments, uiState.isSending) {
        if (uiState.isSending) return@LaunchedEffect
        if (to.isBlank() && subject.isBlank() && body.isBlank() && uiState.attachments.isEmpty()) {
            return@LaunchedEffect
        }
        delay(1200)
        viewModel.saveDraft(
            to = to.split(",", ";").map { it.trim() },
            subject = subject,
            body = body
        )
    }

    // Периодическое автосохранение черновика (раз в 5 секунд), чтобы соответствовать ожидаемому UX.
    // Важно: не запускаем во время отправки, чтобы не было гонок send vs autosave.
    LaunchedEffect(uiState.isSending, uiState.attachments.size, uiState.draftId) {
        if (uiState.isSending) return@LaunchedEffect
        while (true) {
            delay(5000)
            // Нечего сохранять
            if (latestTo.isBlank() && latestSubject.isBlank() && latestBody.isBlank() && uiState.attachments.isEmpty()) {
                continue
            }
            viewModel.saveDraft(
                to = latestTo.split(",", ";").map { it.trim() },
                subject = latestSubject,
                body = latestBody
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Новое письмо") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { attachmentPicker.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Filled.Attachment, contentDescription = "Добавить вложение")
                    }
                    IconButton(
                        onClick = {
                        viewModel.sendMessage(
                            to = to.split(",", ";")
                                .map { it.trim() }
                                .filter { it.isNotBlank() },
                            subject = subject,
                            body = body,
                            onSuccess = onBack
                        )
                        },
                        enabled = !uiState.isSending
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Отправить")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "От: ${email.ifBlank { "неизвестно" }}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            OutlinedTextField(
                value = to,
                onValueChange = { to = it },
                label = { Text("Кому") },
                placeholder = { Text("example@mail.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it },
                label = { Text("Тема") },
                placeholder = { Text("Тема письма") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Сообщение") },
                placeholder = { Text("Начните писать...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                maxLines = 10
            )
            if (uiState.attachments.isNotEmpty()) {
                Divider()
                Text(
                    text = "Вложения (${uiState.attachments.size}):",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    uiState.attachments.forEach { attachment ->
                        AssistChip(
                            onClick = { viewModel.removeAttachment(attachment) },
                            label = { Text(attachment.filename) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Удалить вложение",
                                    modifier = Modifier.width(12.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                labelColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when {
                    uiState.isSending -> "Отправка..."
                    uiState.isSavingDraft -> "Сохранение черновика..."
                    else -> ""
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}