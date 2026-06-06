package com.mobilemail.ui.messagedetail.content

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobilemail.domain.model.MessageDetail
import com.mobilemail.data.preferences.PreferencesManager
import com.mobilemail.ui.messagedetail.ClickableTextWithLinks

@Composable
internal fun MessageBodySection(
    message: MessageDetail,
    isExpandedLayout: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val preferencesManager = remember(context) { PreferencesManager(context) }
    val blockRemoteContent by preferencesManager.blockRemoteContent.collectAsStateWithLifecycle(initialValue = true)
    var allowRemoteContentForMessage by remember(message.id) {
        mutableStateOf(RemoteContentAllowanceStore.isAllowed(message.id))
    }

    Column(modifier = modifier.fillMaxWidth()) {
        message.body.html?.let { html ->
            val sanitizedHtml = sanitizeHtmlForWebView(html)
            val adaptedHtml = HtmlDocumentComposer.composeResponsiveDocument(sanitizedHtml)
            val contentKey = "${message.id}:${adaptedHtml.hashCode()}"
            val blockRemoteLoads = blockRemoteContent && !allowRemoteContentForMessage

            if (blockRemoteLoads) {
                RemoteContentBlockedCard(
                    onAllowRemoteContent = {
                        allowRemoteContentForMessage = true
                        RemoteContentAllowanceStore.allow(message.id)
                    }
                )
            }

            HtmlMessageWebView(
                contentKey = contentKey,
                htmlDocument = adaptedHtml,
                isExpandedLayout = isExpandedLayout,
                blockRemoteLoads = blockRemoteLoads,
            )
        } ?: message.body.text?.let { text ->
            ClickableTextWithLinks(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(vertical = 4.dp),
                context = context
            )
        } ?: Text(
            text = "Письмо не содержит текста",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RemoteContentBlockedCard(onAllowRemoteContent: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                text = "Удалённый контент заблокирован",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Внешние изображения и трекеры отключены для защиты конфиденциальности.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            TextButton(
                onClick = onAllowRemoteContent,
                modifier = Modifier.align(Alignment.End),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text("Показать изображения")
            }
        }
    }
}
