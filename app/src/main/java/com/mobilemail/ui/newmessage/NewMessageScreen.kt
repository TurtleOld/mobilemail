package com.mobilemail.ui.newmessage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextField
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
import com.mobilemail.domain.model.Attachment
import com.mobilemail.data.preferences.PreferencesManager
import com.mobilemail.ui.common.FeatureScreenEffects
import com.mobilemail.ui.common.rememberFeatureScreenSnackbarHostState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi

@Suppress("UnusedParameter")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewMessageScreen(
    viewModel: ComposeViewModel,
    server: String,
    email: String,
    accountId: String,
    initialTo: String = "",
    initialSubject: String = "",
    initialBody: String = "",
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = rememberFeatureScreenSnackbarHostState()
    var to by remember(initialTo) { mutableStateOf(initialTo) }
    var subject by remember(initialSubject) { mutableStateOf(initialSubject) }
    var body by remember(initialBody) { mutableStateOf(initialBody) }
    val context = LocalContext.current
    val preferencesManager = remember(context) { PreferencesManager(context) }
    val attachmentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri ->
            viewModel.addAttachment(context.contentResolver, uri)
        }
    }

    LaunchedEffect(server, email) {
        val signature = preferencesManager.getSignature(server, email).orEmpty()
        if (signature.isNotBlank() && body.isBlank()) {
            body = "\n\n$signature"
        }
    }

    FeatureScreenEffects(
        uiState = uiState,
        onErrorConsumed = viewModel::clearError,
        onNotificationConsumed = viewModel::clearNotification,
        snackbarHostState = snackbarHostState,
    )

    data class DraftSnapshot(
        val to: String,
        val subject: String,
        val body: String,
        val attachmentIds: List<String>
    )

    val draftSnapshot = remember(to, subject, body, uiState.attachments) {
        DraftSnapshot(
            to = to,
            subject = subject,
            body = body,
            attachmentIds = uiState.attachments.map { it.id }
        )
    }
    var lastAutosavedSnapshot by remember { mutableStateOf<DraftSnapshot?>(null) }

    LaunchedEffect(draftSnapshot, uiState.isSending) {
        if (uiState.isSending) return@LaunchedEffect
        val isEmptyDraft = draftSnapshot.to.isBlank() &&
            draftSnapshot.subject.isBlank() &&
            draftSnapshot.body.isBlank() &&
            draftSnapshot.attachmentIds.isEmpty()
        if (isEmptyDraft || draftSnapshot == lastAutosavedSnapshot) {
            return@LaunchedEffect
        }

        delay(1200)
        viewModel.saveDraft(
            to = draftSnapshot.to.split(",", ";").map { it.trim() },
            subject = draftSnapshot.subject,
            body = draftSnapshot.body
        )
        lastAutosavedSnapshot = draftSnapshot
    }

    NewMessageScreenContent(
        email = email,
        to = to,
        onToChange = { to = it },
        subject = subject,
        onSubjectChange = { subject = it },
        body = body,
        onBodyChange = { body = it },
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onPickAttachments = { attachmentPicker.launch(arrayOf("*/*")) },
        onSend = {
            viewModel.sendMessage(
                to = to.split(",", ";")
                    .map { it.trim() }
                    .filter { it.isNotBlank() },
                subject = subject,
                body = body,
                onSuccess = onBack
            )
        },
        onRemoveAttachment = viewModel::removeAttachment
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun NewMessageScreenContent(
    email: String,
    to: String,
    onToChange: (String) -> Unit,
    subject: String,
    onSubjectChange: (String) -> Unit,
    body: String,
    onBodyChange: (String) -> Unit,
    uiState: ComposeUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onPickAttachments: () -> Unit,
    onSend: () -> Unit,
    onRemoveAttachment: (Attachment) -> Unit
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Новое письмо") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = onPickAttachments) {
                        Icon(Icons.Filled.Attachment, contentDescription = "Добавить вложение")
                    }
                    IconButton(
                        onClick = onSend,
                        enabled = !uiState.isSending
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить")
                    }
                }
            )
        }
    ) { padding ->
        NewMessageFormContent(
            email = email,
            to = to,
            onToChange = onToChange,
            subject = subject,
            onSubjectChange = onSubjectChange,
            body = body,
            onBodyChange = onBodyChange,
            uiState = uiState,
            onRemoveAttachment = onRemoveAttachment,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NewMessageFormContent(
    email: String,
    to: String,
    onToChange: (String) -> Unit,
    subject: String,
    onSubjectChange: (String) -> Unit,
    body: String,
    onBodyChange: (String) -> Unit,
    uiState: ComposeUiState,
    onRemoveAttachment: (Attachment) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val transparentColors = TextFieldDefaults.colors(
            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
        )
        Text(
            text = "От: ${email.ifBlank { "неизвестно" }}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        TextField(
            value = to,
            onValueChange = onToChange,
            label = { Text("Кому") },
            placeholder = { Text("example@mail.com") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = transparentColors,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        TextField(
            value = subject,
            onValueChange = onSubjectChange,
            label = { Text("Тема") },
            placeholder = { Text("Тема письма") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = transparentColors,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        TextField(
            value = body,
            onValueChange = onBodyChange,
            placeholder = { Text("Текст письма…") },
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            maxLines = 10,
            colors = transparentColors,
        )
        if (uiState.attachments.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
                        onClick = { onRemoveAttachment(attachment) },
                        label = {
                            Text(
                                if (attachment.isUploaded) attachment.filename
                                else "${attachment.filename} (offline)"
                            )
                        },
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
