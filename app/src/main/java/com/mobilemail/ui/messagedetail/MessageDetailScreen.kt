package com.mobilemail.ui.messagedetail

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageDetailScreen(
    viewModel: MessageDetailViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Письмо") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
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
                    Text(uiState.error ?: "Письмо не найдено")
                }
            }
        }
    }
}

@Composable
fun MessageContent(
    message: com.mobilemail.data.model.MessageDetail,
    modifier: Modifier = Modifier
) {
    val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    val scrollState = rememberScrollState()

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
            androidx.compose.runtime.LaunchedEffect(message.to) {
                android.util.Log.d("MessageDetailScreen", "Отображение поля 'Кому': size=${message.to.size}")
                message.to.forEachIndexed { index, addr ->
                    android.util.Log.d("MessageDetailScreen", "  [$index] name='${addr.name}', email='${addr.email}'")
                }
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
                        val displayText = when {
                            !addr.name.isNullOrBlank() -> addr.name!!
                            addr.email.isNotBlank() -> addr.email
                            else -> "(без адреса)"
                        }
                        android.util.Log.d("MessageDetailScreen", "Отображение адреса: name='${addr.name}', email='${addr.email}', display='$displayText'")
                        displayText
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

        message.body.html?.let { html ->
            // Добавляем мета-тег viewport для адаптации под мобильные устройства
            val adaptedHtml = if (html.contains("<head>", ignoreCase = true)) {
                html.replace("<head>", "<head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes\">", ignoreCase = true)
            } else if (html.contains("<html>", ignoreCase = true)) {
                html.replace("<html>", "<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes\"></head>", ignoreCase = true)
            } else {
                "<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes\"><style>body { margin: 0; padding: 8px; word-wrap: break-word; } img { max-width: 100%; height: auto; }</style></head><body>$html</body></html>"
            }
            
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        webViewClient = WebViewClient()
                        settings.apply {
                            javaScriptEnabled = true
                            // Настройки для адаптации под мобильные устройства
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            builtInZoomControls = true
                            displayZoomControls = false
                            setSupportZoom(true)
                            // Разрешаем масштабирование
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
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(vertical = 8.dp)
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
