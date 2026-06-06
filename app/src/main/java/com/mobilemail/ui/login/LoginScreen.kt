package com.mobilemail.ui.login

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.mobilemail.ui.common.isExpandedWindowWidth
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
import kotlinx.coroutines.delay

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: (String, String, String, String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = rememberFeatureScreenSnackbarHostState()
    val isExpandedLayout = isExpandedWindowWidth()
    var hasOpenedAuthPage by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val verificationUri = uiState.oauthVerificationUriComplete ?: uiState.oauthVerificationUri

    // Remaining seconds until device-code expires; null when no code is active.
    var remainingSeconds by remember(uiState.oauthExpiresAt) {
        mutableStateOf(uiState.oauthExpiresAt?.let { ((it - System.currentTimeMillis()) / 1000).coerceAtLeast(0) })
    }
    LaunchedEffect(uiState.oauthExpiresAt) {
        val expiresAt = uiState.oauthExpiresAt ?: return@LaunchedEffect
        while (true) {
            val remaining = ((expiresAt - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
            remainingSeconds = remaining
            if (remaining <= 0L) break
            delay(1_000)
        }
    }
    val isCodeExpired = remainingSeconds != null && remainingSeconds!! <= 0L

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
                        val cardColor = if (isCodeExpired)
                            MaterialTheme.colorScheme.errorContainer
                        else
                            MaterialTheme.colorScheme.primaryContainer
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = cardColor)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (isCodeExpired) {
                                    Text(
                                        text = "Время ожидания истекло",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = "Код авторизации больше не действителен. Начните процесс заново.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Button(
                                        onClick = {
                                            viewModel.cancelOAuthLogin()
                                            hasOpenedAuthPage = false
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error
                                        )
                                    ) {
                                        Text("Войти заново")
                                    }
                                } else {
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
                                    remainingSeconds?.let { secs ->
                                        val mins = secs / 60
                                        val s = secs % 60
                                        Text(
                                            text = "Действителен ещё %d:%02d".format(mins, s),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (secs <= 30)
                                                MaterialTheme.colorScheme.error
                                            else
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
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
    val parsed = uri.toUri()
    if (parsed.scheme != "https" && parsed.scheme != "http") return
    val intent = Intent(Intent.ACTION_VIEW, parsed).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
