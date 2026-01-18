package com.mobilemail.ui.messages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobilemail.data.model.MessageListItem
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    viewModel: MessagesViewModel,
    onMessageClick: (String) -> Unit,
    onSearchClick: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Логирование состояния
    LaunchedEffect(uiState.isLoading, uiState.folders.size, uiState.messages.size, uiState.error) {
        android.util.Log.d("MessagesScreen", "Состояние UI: isLoading=${uiState.isLoading}, folders=${uiState.folders.size}, messages=${uiState.messages.size}, error=${uiState.error?.getUserMessage()}")
    }

    // Обработка ошибок
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
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(uiState.selectedFolder?.name ?: "Почта") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Меню")
                        }
                    },
                    actions = {
                        IconButton(onClick = onSearchClick) {
                            Icon(Icons.Default.Search, contentDescription = "Поиск")
                        }
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                        }
                        IconButton(onClick = onLogout) {
                            Icon(Icons.Default.ExitToApp, contentDescription = "Выход")
                        }
                    }
                )
            }
        ) { padding ->
            MessagesList(
                messages = uiState.messages,
                isLoading = uiState.isLoading,
                isLoadingMore = uiState.isLoadingMore,
                hasMore = uiState.hasMore,
                onMessageClick = onMessageClick,
                onLoadMore = { viewModel.loadMoreMessages() },
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
        items(folders.size) { index ->
            val folder = folders[index]
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
    isLoadingMore: Boolean,
    hasMore: Boolean,
    onMessageClick: (String) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(messages.size, isLoading) {
        android.util.Log.d("MessagesScreen", "MessagesList: messages=${messages.size}, isLoading=$isLoading")
    }
    
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        if (isLoading && messages.isEmpty()) {
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
                items(messages.size) { index ->
                    val message = messages[index]
                    MessageItem(
                        message = message,
                        onClick = { onMessageClick(message.id) }
                    )
                    if (index == messages.size - 1 && hasMore && !isLoadingMore) {
                        LaunchedEffect(Unit) {
                            onLoadMore()
                        }
                    }
                }
                
                if (isLoadingMore) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
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
    val isUnread = message.flags.unread
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isUnread) 4.dp else 2.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isUnread) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else 
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isUnread) {
                        Icon(
                            Icons.Default.Email,
                            contentDescription = "Непрочитанное",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = message.from.name ?: message.from.email,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = dateFormat.format(message.date),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isUnread) FontWeight.Medium else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = message.subject,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            if (message.snippet.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message.snippet,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isUnread) FontWeight.Medium else FontWeight.Normal,
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
