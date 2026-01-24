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
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.util.Log
import com.mobilemail.data.preferences.PreferencesManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewMessageScreen(
    server: String,
    email: String,
    password: String,
    accountId: String,
    onBack: () -> Unit
) {
    Log.d("NewMessageScreen", "Init: server=$server, email=$email, accountId=$accountId, passwordPlaceholder=${password == "-"}")
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var to by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    val context = LocalContext.current
    val preferencesManager = remember(context) { PreferencesManager(context) }

    LaunchedEffect(server, email) {
        val signature = preferencesManager.getSignature(server, email).orEmpty()
        if (signature.isNotBlank() && body.isBlank()) {
            body = "\n\n$signature"
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
                    IconButton(onClick = {
                        scope.launch {
                            snackbarHostState.showSnackbar("Отправка пока не реализована")
                        }
                    }) {
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
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Отправка будет доступна позже.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}