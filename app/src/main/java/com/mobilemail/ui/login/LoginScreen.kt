package com.mobilemail.ui.login

import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobilemail.R
import kotlinx.coroutines.launch
import androidx.compose.ui.window.Dialog

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: (String, String, String, String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var hasOpenedAuthPage by remember { mutableStateOf(false) }
    var showAuthWebView by remember { mutableStateOf(false) }
    var authWebViewUrl by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val verificationUri = uiState.oauthVerificationUriComplete ?: uiState.oauthVerificationUri

    LaunchedEffect(uiState.account) {
        uiState.account?.let { account ->
            showAuthWebView = false
            authWebViewUrl = null
            onLoginSuccess(
                uiState.server,
                account.email,
                "",
                account.id
            )
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            showAuthWebView = false
            authWebViewUrl = null
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = error.getUserMessage(),
                    duration = SnackbarDuration.Long
                )
                viewModel.clearError()
            }
        }
    }

    LaunchedEffect(verificationUri, uiState.oauthUserCode) {
        if (!verificationUri.isNullOrBlank() && uiState.oauthUserCode != null && !hasOpenedAuthPage) {
            authWebViewUrl = verificationUri
            showAuthWebView = true
            hasOpenedAuthPage = true
        }
        if (uiState.oauthUserCode == null) {
            hasOpenedAuthPage = false
            showAuthWebView = false
            authWebViewUrl = null
        }
    }

    if (showAuthWebView && authWebViewUrl != null) {
        Dialog(onDismissRequest = {
            showAuthWebView = false
        }) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 4.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showAuthWebView = false }) {
                            Text("Закрыть")
                        }
                    }
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewClient = WebViewClient()
                                settings.javaScriptEnabled = true
                                loadUrl(authWebViewUrl!!)
                            }
                        },
                        update = { webView ->
                            if (webView.url != authWebViewUrl) {
                                webView.loadUrl(authWebViewUrl!!)
                            }
                        }
                    )
                }
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

            val oauthUserCode = uiState.oauthUserCode
            if (oauthUserCode != null) {
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
                            text = oauthUserCode,
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        if (verificationUri != null) {
                            Button(
                                onClick = {
                                    authWebViewUrl = verificationUri
                                    showAuthWebView = true
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
                    keyboardActions = KeyboardActions(onDone = { }),
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
