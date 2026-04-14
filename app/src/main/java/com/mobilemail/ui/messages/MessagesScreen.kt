package com.mobilemail.ui.messages

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobilemail.data.model.FolderRole
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
    onComposeClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isExpandedLayout = LocalConfiguration.current.screenWidthDp >= 840
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showMoveDialog by remember { mutableStateOf(false) }

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

    LaunchedEffect(uiState.notification) {
        when (val notification = uiState.notification) {
            is com.mobilemail.ui.common.NotificationState.Snackbar -> {
                scope.launch {
                    snackbarHostState.showSnackbar(notification.message)
                    viewModel.clearNotification()
                }
            }
            else -> Unit
        }
    }

    if (showMoveDialog) {
        val currentFolderId = uiState.selectedFolder?.id
        AlertDialog(
            onDismissRequest = { showMoveDialog = false },
            title = { Text("Переместить в папку") },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp)
                ) {
                    items(
                        uiState.folders.filter { it.id != currentFolderId },
                        key = { it.id }
                    ) { folder ->
                        ListItem(
                            headlineContent = { Text(folder.name) },
                            supportingContent = {
                                Text(
                                    when (folder.role) {
                                        FolderRole.ARCHIVE -> "Архив"
                                        FolderRole.SPAM -> "Спам"
                                        FolderRole.TRASH -> "Корзина"
                                        FolderRole.SENT -> "Отправленные"
                                        FolderRole.DRAFTS -> "Черновики"
                                        FolderRole.INBOX -> "Входящие"
                                        FolderRole.CUSTOM -> "Папка"
                                    }
                                )
                            },
                            modifier = Modifier.clickable {
                                viewModel.moveSelected(folder.id)
                                showMoveDialog = false
                            }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMoveDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    val navigationContent: @Composable () -> Unit = {
        MailNavigationContent(
            folders = uiState.folders,
            selectedFolder = uiState.selectedFolder,
            onComposeClick = onComposeClick,
            onSettingsClick = onSettingsClick,
            onFolderSelected = { folder ->
                viewModel.selectFolder(folder)
                if (!isExpandedLayout) {
                    scope.launch { drawerState.close() }
                }
            }
        )
    }

    val screenContent: @Composable () -> Unit = {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (uiState.selectedMessageIds.isNotEmpty()) {
                    TopAppBar(
                        title = { Text("${uiState.selectedMessageIds.size} выбрано") },
                        navigationIcon = {
                            IconButton(onClick = { viewModel.clearSelection() }) {
                                Icon(Icons.Default.Close, contentDescription = "Отменить выбор")
                            }
                        },
                        actions = {
                            IconButton(onClick = { viewModel.archiveSelected() }) {
                                Icon(Icons.Default.Archive, contentDescription = "Архивировать")
                            }
                            IconButton(onClick = { viewModel.reportSpamSelected() }) {
                                Icon(Icons.Default.Report, contentDescription = "В спам")
                            }
                            IconButton(onClick = { showMoveDialog = true }) {
                                Icon(Icons.Default.DriveFileMove, contentDescription = "Переместить")
                            }
                            IconButton(onClick = { viewModel.selectAll() }) {
                                Icon(Icons.Default.SelectAll, contentDescription = "Выбрать все")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                } else {
                    TopAppBar(
                        title = { Text(uiState.selectedFolder?.name ?: "Почта") },
                        navigationIcon = {
                            if (!isExpandedLayout) {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Menu, contentDescription = "Меню")
                                }
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
            }
        ) { padding ->
            MessagesList(
                messages = uiState.messages,
                isLoading = uiState.isLoading,
                isLoadingMore = uiState.isLoadingMore,
                hasMore = uiState.hasMore,
                selectedIds = uiState.selectedMessageIds,
                onMessageClick = { messageId ->
                    if (uiState.selectedMessageIds.isNotEmpty()) {
                        viewModel.toggleMessageSelection(messageId)
                    } else {
                        onMessageClick(messageId)
                    }
                },
                onMessageLongClick = { messageId -> viewModel.toggleMessageSelection(messageId) },
                onLoadMore = { viewModel.loadMoreMessages() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        }
    }

    if (isExpandedLayout) {
        Row(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(300.dp),
                tonalElevation = 1.dp,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                navigationContent()
            }
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .padding(vertical = 12.dp)
            ) {
                Divider(modifier = Modifier.fillMaxSize())
            }
            Box(modifier = Modifier.weight(1f)) {
                screenContent()
            }
        }
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    navigationContent()
                }
            }
        ) {
            screenContent()
        }
    }
}

@Composable
private fun MailNavigationContent(
    folders: List<com.mobilemail.data.model.Folder>,
    selectedFolder: com.mobilemail.data.model.Folder?,
    onComposeClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onFolderSelected: (com.mobilemail.data.model.Folder) -> Unit
) {
    Column(modifier = Modifier.fillMaxHeight()) {
        NavigationDrawerItem(
            icon = {
                Box(modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Default.Email,
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(20.dp)
                    )
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(12.dp)
                    )
                }
            },
            label = { Text("Написать письмо") },
            selected = false,
            onClick = onComposeClick,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = { Text("Настройки") },
            selected = false,
            onClick = onSettingsClick,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        FoldersList(
            folders = folders,
            selectedFolder = selectedFolder,
            onFolderSelected = onFolderSelected,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun FoldersList(
    folders: List<com.mobilemail.data.model.Folder>,
    selectedFolder: com.mobilemail.data.model.Folder?,
    onFolderSelected: (com.mobilemail.data.model.Folder) -> Unit,
    modifier: Modifier = Modifier
) {
    val defaultOrder = listOf(
        FolderRole.INBOX,
        FolderRole.DRAFTS,
        FolderRole.SENT,
        FolderRole.SPAM,
        FolderRole.TRASH
    )
    val defaultFolders = defaultOrder.mapNotNull { role ->
        folders.firstOrNull { it.role == role }
    }
    val customFolders = folders
        .filter { it.role !in defaultOrder }
        .sortedBy { it.name.lowercase(Locale.getDefault()) }

    LazyColumn(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(defaultFolders, key = { it.id }) { folder ->
            FolderItem(
                folder = folder,
                isSelected = folder.id == selectedFolder?.id,
                onClick = { onFolderSelected(folder) }
            )
        }

        if (defaultFolders.isNotEmpty() && customFolders.isNotEmpty()) {
            item {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
            }
        }

        items(customFolders, key = { it.id }) { folder ->
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
            .semantics(mergeDescendants = true) {
                role = Role.Tab
                selected = isSelected
                contentDescription = if (folder.unreadCount > 0) {
                    "${folder.name}, непрочитанных ${folder.unreadCount}"
                } else {
                    folder.name
                }
            }
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
    selectedIds: Set<String>,
    onMessageClick: (String) -> Unit,
    onMessageLongClick: (String) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(listState, messages, hasMore, isLoading, isLoadingMore) {
        if (messages.isEmpty() || !hasMore || isLoading || isLoadingMore) return@LaunchedEffect
        val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@LaunchedEffect
        if (lastVisibleIndex >= messages.lastIndex) {
            onLoadMore()
        }
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
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(items = messages, key = { it.id }) { message ->
                    MessageItem(
                        message = message,
                        isSelected = selectedIds.contains(message.id),
                        onClick = { onMessageClick(message.id) },
                        onLongClick = { onMessageLongClick(message.id) }
                    )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageItem(
    message: MessageListItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    val isUnread = message.flags.unread

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                role = Role.Button
                stateDescription = buildString {
                    append(if (isUnread) "Непрочитанное" else "Прочитанное")
                    if (message.flags.starred) append(", в избранном")
                    if (message.flags.hasAttachments) append(", с вложением")
                    if (isSelected) append(", выбрано")
                }
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isUnread) 4.dp else 2.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> MaterialTheme.colorScheme.primaryContainer
                isUnread -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Выбрано",
                    modifier = Modifier
                        .size(20.dp)
                        .padding(end = 0.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isUnread && !isSelected) {
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
}
