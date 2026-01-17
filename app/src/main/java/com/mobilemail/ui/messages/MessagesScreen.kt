package com.mobilemail.ui.messages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobilemail.data.model.FolderRole
import com.mobilemail.data.model.MessageListItem
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    viewModel: MessagesViewModel,
    onMessageClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    androidx.compose.runtime.LaunchedEffect(Unit) {
        android.util.Log.d("MessagesScreen", "MessagesScreen создан")
    }
    
    androidx.compose.runtime.LaunchedEffect(uiState.messages.size, uiState.isLoading, uiState.selectedFolder?.id) {
        android.util.Log.d("MessagesScreen", "Состояние изменилось: messages=${uiState.messages.size}, isLoading=${uiState.isLoading}, folder=${uiState.selectedFolder?.name}")
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                FoldersList(
                    folders = uiState.folders,
                    selectedFolder = uiState.selectedFolder,
                    onFolderSelected = { folder ->
                        viewModel.selectFolder(folder)
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.fillMaxHeight()
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(uiState.selectedFolder?.name ?: "Почта") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Меню")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                        }
                    }
                )
            }
        ) { padding ->
            MessagesList(
                messages = uiState.messages,
                isLoading = uiState.isLoading,
                onMessageClick = onMessageClick,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        }
    }
}

@Composable
fun FoldersList(
    folders: List<com.mobilemail.data.model.Folder>,
    selectedFolder: com.mobilemail.data.model.Folder?,
    onFolderSelected: (com.mobilemail.data.model.Folder) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(folders) { folder ->
            FolderItem(
                folder = folder,
                isSelected = folder.id == selectedFolder?.id,
                onClick = { onFolderSelected(folder) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderItem(
    folder: com.mobilemail.data.model.Folder,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Email,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = folder.name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            if (folder.unreadCount > 0) {
                Badge {
                    Text(folder.unreadCount.toString())
                }
            }
        }
    }
}

@Composable
fun MessagesList(
    messages: List<MessageListItem>,
    isLoading: Boolean,
    onMessageClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.runtime.LaunchedEffect(messages.size, isLoading) {
        android.util.Log.d("MessagesScreen", "MessagesList: messages=${messages.size}, isLoading=$isLoading")
    }
    
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        } else if (messages.isEmpty()) {
            Text(
                text = "Нет писем",
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(messages) { message ->
                    MessageItem(
                        message = message,
                        onClick = { onMessageClick(message.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun MessageItem(
    message: MessageListItem,
    onClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = message.from.name ?: message.from.email,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dateFormat.format(message.date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = message.subject,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            if (message.snippet.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message.snippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            if (message.flags.hasAttachments) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "📎",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
