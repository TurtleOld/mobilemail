package com.mobilemail.ui.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.mobilemail.data.security.PinManager

private fun Context.findActivity(): FragmentActivity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is FragmentActivity) return context
        context = context.baseContext
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinSetupScreen(
    viewModel: PinSetupViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val isExpandedLayout = LocalConfiguration.current.screenWidthDp >= 840
    val centeredCardModifier = if (isExpandedLayout) {
        Modifier
            .fillMaxWidth()
            .widthIn(max = 640.dp)
    } else {
        Modifier.fillMaxWidth()
    }

    // Обработка запроса биометрии при включении
    LaunchedEffect(uiState.showBiometricPrompt) {
        val activity = context.findActivity()
        android.util.Log.d("PinSetupScreen", "showBiometricPrompt=${uiState.showBiometricPrompt}, activity=$activity")
        if (uiState.showBiometricPrompt && activity != null) {
            android.util.Log.d("PinSetupScreen", "Launching biometric prompt")
            val executor = ContextCompat.getMainExecutor(activity)
            val biometricPrompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        android.util.Log.d("PinSetupScreen", "Biometric auth succeeded")
                        viewModel.onBiometricSuccess()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        android.util.Log.d("PinSetupScreen", "Biometric auth error: $errorCode - $errString")
                        if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                            errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                            viewModel.onBiometricError(errString.toString())
                        } else {
                            viewModel.onBiometricDismissed()
                        }
                    }

                    override fun onAuthenticationFailed() {
                        android.util.Log.d("PinSetupScreen", "Biometric auth failed (wrong finger)")
                    }
                }
            )

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Включение биометрии")
                .setSubtitle("Подтвердите биометрией для включения")
                .setNegativeButtonText("Отмена")
                .build()

            biometricPrompt.authenticate(promptInfo)
        }
    }

    LaunchedEffect(uiState.showSavedMessage) {
        if (uiState.showSavedMessage) {
            snackbarHostState.showSnackbar("PIN-код сохранён")
            viewModel.resetSavedMessage()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Настройки входа") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
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
                .padding(horizontal = if (isExpandedLayout) 24.dp else 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = centeredCardModifier
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                                    text = "Вход по PIN-коду",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Защитите приложение PIN-кодом",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = uiState.isPinEnabled,
                            onCheckedChange = { viewModel.onPinEnabledChanged(it) }
                        )
                    }
                }
            }

            // Показываем форму ввода PIN только если PIN ещё не сохранён
            if (uiState.isPinEnabled && !uiState.isPinSaved) {
                Card(
                    modifier = centeredCardModifier
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (!uiState.isConfirmStep) {
                            Text(
                                text = "Введите новый PIN-код",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )
                            OutlinedTextField(
                                value = uiState.pin,
                                onValueChange = { viewModel.onPinChanged(it) },
                                label = { Text("PIN-код (${PinManager.PIN_LENGTH} цифр)") },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                isError = uiState.error != null
                            )
                            Button(
                                onClick = { viewModel.onPinEntered() },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = uiState.pin.length == PinManager.PIN_LENGTH
                            ) {
                                Text("Далее")
                            }
                        } else {
                            Text(
                                text = "Подтвердите PIN-код",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )
                            OutlinedTextField(
                                value = uiState.confirmPin,
                                onValueChange = { viewModel.onConfirmPinChanged(it) },
                                label = { Text("Повторите PIN-код") },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                isError = uiState.error != null
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TextButton(
                                    onClick = { viewModel.goBackToPin() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Назад")
                                }
                                Button(
                                    onClick = { viewModel.onConfirmPinEntered() },
                                    modifier = Modifier.weight(1f),
                                    enabled = uiState.confirmPin.length == PinManager.PIN_LENGTH
                                ) {
                                    Text("Сохранить")
                                }
                            }
                        }
                    }
                }
            }

            // Показываем биометрию только когда PIN уже сохранён
            if (uiState.isPinSaved && uiState.isBiometricAvailable) {
                Card(
                    modifier = centeredCardModifier
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Default.Fingerprint,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column {
                                    Text(
                                        text = "Биометрия",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "Разблокировка отпечатком или лицом",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = uiState.isBiometricEnabled,
                                onCheckedChange = { viewModel.onBiometricToggleRequested(it) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
