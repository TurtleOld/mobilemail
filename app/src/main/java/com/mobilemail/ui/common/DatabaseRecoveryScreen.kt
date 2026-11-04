package com.mobilemail.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mobilemail.data.local.database.DatabaseOpenException

/**
 * Explains that the encrypted database could not be opened and that its data was preserved.
 *
 * @param error Failure reported while opening the database.
 * @param onRetry Called when the user asks to open the database again.
 * @param modifier Modifier applied to the root layout.
 */
@Composable
fun DatabaseRecoveryScreen(
    error: Throwable,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "База данных недоступна",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = recoveryMessage(error),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Text("Повторить")
        }
    }
}

private fun recoveryMessage(error: Throwable): String {
    return if (error is DatabaseOpenException && error.isWrongPassphrase) {
        "Не удалось открыть зашифрованную базу данных: ключ шифрования не подходит. " +
            "Сообщения и очередь неотправленных писем сохранены. " +
            "Восстановите доступ к исходному ключу и повторите попытку."
    } else {
        "Не удалось открыть базу данных. Сообщения и очередь неотправленных писем сохранены. " +
            "Проверьте доступ к ключу шифрования и повторите попытку."
    }
}
