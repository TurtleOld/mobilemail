package com.mobilemail.ui.messages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Drafts
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mobilemail.data.preferences.SavedSession
import com.mobilemail.domain.model.Folder
import com.mobilemail.domain.model.FolderRole
import com.mobilemail.ui.common.MonogramAvatar

private fun folderIcon(role: FolderRole): ImageVector = when (role) {
    FolderRole.INBOX   -> Icons.Default.Inbox
    FolderRole.SENT    -> Icons.AutoMirrored.Filled.Send
    FolderRole.DRAFTS  -> Icons.Default.Drafts
    FolderRole.TRASH   -> Icons.Default.Delete
    FolderRole.SPAM    -> Icons.Default.Shield
    FolderRole.ARCHIVE -> Icons.Default.Archive
    FolderRole.CUSTOM  -> Icons.Default.Inbox
}

@Composable
fun MailDrawerContent(
    currentSession: SavedSession,
    accounts: List<SavedSession>,
    folders: List<Folder>,
    selectedFolderId: String?,
    onFolderSelected: (Folder) -> Unit,
    onSwitchAccount: (SavedSession) -> Unit,
    onCompose: () -> Unit,
    onQueue: () -> Unit,
    onSettings: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val accountName = currentSession.email.substringBefore("@")

    ModalDrawerSheet(drawerContainerColor = cs.surface) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            // Заголовок аккаунта
            Column(
                Modifier.padding(
                    start = 22.dp, top = 24.dp, end = 22.dp, bottom = 14.dp
                )
            ) {
                MonogramAvatar(name = accountName, size = 52.dp)
                Text(
                    text = accountName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
                TextButton(
                    onClick = { accounts.firstOrNull()?.let(onSwitchAccount) },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(
                        text = currentSession.email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                    )
                    Icon(Icons.Default.ArrowDropDown, null, tint = cs.onSurfaceVariant)
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = cs.outlineVariant,
            )

            // Действия
            Spacer(Modifier.padding(4.dp))
            DrawerActionItem("Написать письмо", Icons.Default.Edit, onClick = onCompose)
            DrawerActionItem("Очередь", Icons.Default.Schedule, onClick = onQueue)
            DrawerActionItem("Настройки", Icons.Default.Settings, onClick = onSettings)

            HorizontalDivider(
                modifier = Modifier.padding(16.dp),
                color = cs.outlineVariant,
            )
            DrawerSectionLabel("Папки")

            folders.forEach { folder ->
                DrawerFolderItem(
                    folder = folder,
                    isSelected = folder.id == selectedFolderId,
                    onClick = { onFolderSelected(folder) },
                )
            }
            Spacer(Modifier.padding(8.dp))
        }
    }
}

@Composable
private fun DrawerActionItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        label = { Text(label) },
        selected = false,
        onClick = onClick,
        icon = { Icon(icon, null) },
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
    )
}

@Composable
private fun DrawerFolderItem(
    folder: Folder,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        label = { Text(folder.name) },
        selected = isSelected,
        onClick = onClick,
        icon = { Icon(folderIcon(folder.role), null) },
        badge = if (folder.unreadCount > 0) {
            { Text(folder.unreadCount.toString()) }
        } else null,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
    )
}

@Composable
private fun DrawerSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 28.dp, top = 4.dp, bottom = 8.dp),
    )
}
