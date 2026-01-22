package com.mobilemail.ui.login

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobilemail.R
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: (String, String, String, String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState.account) {
        uiState.account?.let { account ->
            onLoginSuccess(
                uiState.server,
                uiState.server,
                "",
                account.id
            )
        }
    }
    
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = error.getUserMessage(),
                    duration = SnackbarDuration.Long
                )
                viewModel.clearError()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.login_title),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = uiState.server,
            onValueChange = viewModel::updateServer,
            label = { Text(stringResource(R.string.server_hint)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = if (uiState.oauthUserCode != null) 16.dp else 24.dp),
            singleLine = true,
            placeholder = { Text("https://mail.example.com") }
        )
        
        if (uiState.oauthUserCode != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Код авторизации:",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = uiState.oauthUserCode ?: "",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    val context = LocalContext.current
                    val verificationUri = uiState.oauthVerificationUriComplete ?: uiState.oauthVerificationUri
                    if (verificationUri != null) {
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(verificationUri))
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Открыть страницу авторизации")
                        }
                    }
                    TextButton(
                        onClick = { viewModel.cancelOAuthLogin() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Отменить")
                    }
                }
            }
        }

        if (uiState.requiresTwoFactor) {
            OutlinedTextField(
                value = uiState.totpCode,
                onValueChange = { newValue ->
                    if (newValue.length <= 6 && newValue.all { it.isDigit() }) {
                        viewModel.updateTotpCode(newValue)
                    }
                },
                label = { Text("Код двухфакторной авторизации") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
                    .semantics {
                        contentDescription = "TOTP код двухфакторной авторизации"
                    },
                singleLine = true,
                placeholder = { Text("000000") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { viewModel.login { } }
                ),
                visualTransformation = PasswordVisualTransformation()
            )
        }

        Button(
            onClick = { viewModel.startOAuthLogin { } },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !uiState.isLoading && (uiState.oauthUserCode == null)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Войти через OAuth")
            }
        }
    }
    }
}
