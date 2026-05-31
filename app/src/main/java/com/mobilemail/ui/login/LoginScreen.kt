package com.mobilemail.ui.login

import android.net.Uri
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobilemail.R
import com.mobilemail.ui.common.FeatureScreenEffects
import com.mobilemail.ui.common.rememberFeatureScreenSnackbarHostState

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: (String, String, String, String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = rememberFeatureScreenSnackbarHostState()
    val isExpandedLayout = LocalConfiguration.current.screenWidthDp >= 840
    var hasOpenedAuthPage by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val verificationUri = uiState.oauthVerificationUriComplete ?: uiState.oauthVerificationUri

    LaunchedEffect(uiState.account) {
        uiState.account?.let { account ->
            onLoginSuccess(
                uiState.server,
                account.email,
                "",
                account.id
            )
        }
    }

    FeatureScreenEffects(
        uiState = uiState,
        onErrorConsumed = viewModel::clearError,
        onNotificationConsumed = viewModel::clearNotification,
        snackbarHostState = snackbarHostState,
    )

    LaunchedEffect(verificationUri, uiState.oauthUserCode) {
        if (!verificationUri.isNullOrBlank() && uiState.oauthUserCode != null && !hasOpenedAuthPage) {
            openAuthorizationPage(context, verificationUri)
            hasOpenedAuthPage = true
        }
        if (uiState.oauthUserCode == null) {
            hasOpenedAuthPage = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = if (isExpandedLayout) 32.dp else 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text(
                        text = stringResource(R.string.login_title),
                        style = MaterialTheme.typography.headlineLarge
                    )

                    OutlinedTextField(
                        value = uiState.server,
                        onValueChange = viewModel::updateServer,
                        label = { Text(stringResource(R.string.server_hint)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "Адрес почтового сервера" },
                        singleLine = true,
                        placeholder = { Text("https://mail.example.com") }
                    )

                    val oauthUserCode = uiState.oauthUserCode
                    if (oauthUserCode != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Код авторизации:",
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Text(
                                    text = oauthUserCode,
                                    style = MaterialTheme.typography.headlineMedium,
                                    modifier = Modifier.semantics {
                                        contentDescription = "Код авторизации $oauthUserCode"
                                    }
                                )
                                if (verificationUri != null) {
                                    Button(
                                        onClick = {
                                            openAuthorizationPage(context, verificationUri)
                                            hasOpenedAuthPage = true
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
    }
}

private fun openAuthorizationPage(context: android.content.Context, uri: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
