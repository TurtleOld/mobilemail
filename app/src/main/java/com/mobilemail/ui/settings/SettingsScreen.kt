package com.mobilemail.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Switch
import androidx.compose.material3.Card
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
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
    onBack: () -> Unit,
    onPinSetupClick: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val isExpandedLayout = LocalConfiguration.current.screenWidthDp >= 840
    var signature by remember { mutableStateOf("") }
    var blockRemoteContent by remember { mutableStateOf(true) }
    var privacyScreenProtection by remember { mutableStateOf(true) }

    LaunchedEffect(server, email) {
        signature = preferencesManager.getSignature(server, email).orEmpty()
        blockRemoteContent = preferencesManager.isBlockRemoteContentEnabled()
        privacyScreenProtection = preferencesManager.isPrivacyScreenProtectionEnabled()
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
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)

        if (isExpandedLayout) {
            Row(
                modifier = contentModifier,
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Column(
                    modifier = Modifier.weight(0.9f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SecuritySection(onPinSetupClick = onPinSetupClick)
                    PrivacySection(
                        blockRemoteContent = blockRemoteContent,
                        privacyScreenProtection = privacyScreenProtection,
                        onBlockRemoteContentChange = { enabled ->
                            blockRemoteContent = enabled
                            scope.launch {
                                preferencesManager.setBlockRemoteContent(enabled)
                            }
                        },
                        onPrivacyScreenProtectionChange = { enabled ->
                            privacyScreenProtection = enabled
                            scope.launch {
                                preferencesManager.setPrivacyScreenProtection(enabled)
                            }
                        }
                    )
                }
                Column(
                    modifier = Modifier.weight(1.1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SignatureSection(
                        signature = signature,
                        onSignatureChange = { signature = it },
                        onSave = {
                            scope.launch {
                                preferencesManager.saveSignature(server, email, signature)
                                snackbarHostState.showSnackbar("Подпись сохранена")
                            }
                        }
                    )
                }
            }
        } else {
            Column(
                modifier = contentModifier,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SecuritySection(onPinSetupClick = onPinSetupClick)
                PrivacySection(
                    blockRemoteContent = blockRemoteContent,
                    privacyScreenProtection = privacyScreenProtection,
                    onBlockRemoteContentChange = { enabled ->
                        blockRemoteContent = enabled
                        scope.launch {
                            preferencesManager.setBlockRemoteContent(enabled)
                        }
                    },
                    onPrivacyScreenProtectionChange = { enabled ->
                        privacyScreenProtection = enabled
                        scope.launch {
                            preferencesManager.setPrivacyScreenProtection(enabled)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                SignatureSection(
                    signature = signature,
                    onSignatureChange = { signature = it },
                    onSave = {
                        scope.launch {
                            preferencesManager.saveSignature(server, email, signature)
                            snackbarHostState.showSnackbar("Подпись сохранена")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun PrivacySection(
    blockRemoteContent: Boolean,
    privacyScreenProtection: Boolean,
    onBlockRemoteContentChange: (Boolean) -> Unit,
    onPrivacyScreenProtectionChange: (Boolean) -> Unit
) {
    Text(
        text = "Конфиденциальность",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            PrivacyToggleRow(
                title = "Блокировать удалённый контент",
                subtitle = "Не загружать внешние изображения и трекеры в письмах",
                checked = blockRemoteContent,
                onCheckedChange = onBlockRemoteContentChange
            )
            PrivacyToggleRow(
                title = "Защита экрана приложения",
                subtitle = "Скрывать содержимое приложения в переключателе задач и на скриншотах",
                checked = privacyScreenProtection,
                onCheckedChange = onPrivacyScreenProtectionChange
            )
        }
    }
}

@Composable
private fun PrivacyToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SecuritySection(
    onPinSetupClick: () -> Unit
) {
    Text(
        text = "Безопасность",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPinSetupClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = "Настроить вход по PIN-коду",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "PIN-код и биометрия",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SignatureSection(
    signature: String,
    onSignatureChange: (String) -> Unit,
    onSave: () -> Unit
) {
    Text(
        text = "Подпись",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium
    )
    OutlinedTextField(
        value = signature,
        onValueChange = onSignatureChange,
        label = { Text("Текст подписи") },
        placeholder = { Text("С уважением, ...") },
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        maxLines = 6
    )
    Spacer(modifier = Modifier.height(4.dp))
    androidx.compose.material3.Button(onClick = onSave) {
        Text("Сохранить")
    }
}
