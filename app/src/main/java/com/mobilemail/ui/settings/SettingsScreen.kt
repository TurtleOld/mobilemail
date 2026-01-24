package com.mobilemail.ui.settings

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mobilemail.data.preferences.PreferencesManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    server: String,
    email: String,
    preferencesManager: PreferencesManager,
    onBack: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var signature by remember { mutableStateOf("") }

    LaunchedEffect(server, email) {
        signature = preferencesManager.getSignature(server, email).orEmpty()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
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
                text = "Подпись",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            OutlinedTextField(
                value = signature,
                onValueChange = { signature = it },
                label = { Text("Текст подписи") },
                placeholder = { Text("С уважением, ...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                maxLines = 6
            )
            Spacer(modifier = Modifier.height(4.dp))
            androidx.compose.material3.Button(
                onClick = {
                    scope.launch {
                        preferencesManager.saveSignature(server, email, signature)
                        snackbarHostState.showSnackbar("Подпись сохранена")
                    }
                }
            ) {
                Text("Сохранить")
            }
        }
    }
}