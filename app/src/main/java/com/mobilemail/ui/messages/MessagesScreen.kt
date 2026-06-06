package com.mobilemail.ui.messages

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import com.mobilemail.domain.model.FolderRole
import com.mobilemail.domain.model.MessageListItem
import com.mobilemail.data.preferences.SavedSession
import com.mobilemail.ui.common.FeatureScreenEffects
import com.mobilemail.ui.common.rememberFeatureScreenSnackbarHostState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    viewModel: MessagesViewModel,
    onMessageClick: (String) -> Unit,
    detailPane: @Composable (String?) -> Unit = {},
    accounts: List<SavedSession> = emptyList(),
    activeAccountEmail: String = "",
    onSearchClick: () -> Unit = {},
    onComposeClick: () -> Unit = {},
    onAddAccountClick: () -> Unit = {},
    onSwitchAccount: (SavedSession) -> Unit = {},
    onOutboxClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pagingItems = viewModel.pagedMessages.collectAsLazyPagingItems()
    val isExpandedLayout = LocalConfiguration.current.screenWidthDp >= 840
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = rememberFeatureScreenSnackbarHostState()
    var showMoveDialog by remember { mutableStateOf(false) }

    FeatureScreenEffects(
        uiState = uiState,
        onErrorConsumed = viewModel::clearError,
        onNotificationConsumed = viewModel::clearNotification,
        snackbarHostState = snackbarHostState,
    )

    LaunchedEffect(pagingItems.itemSnapshotList.items, uiState.hiddenMessageIds) {
        viewModel.updateVisibleMessages(
            pagingItems.itemSnapshotList.items.filterNot { it.id in uiState.hiddenMessageIds }
        )
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

    val currentSession = accounts.firstOrNull { it.email == activeAccountEmail }
        ?: accounts.firstOrNull()

    val navigationContent: @Composable () -> Unit = {
        if (currentSession != null) {
            MailDrawerContent(
                currentSession = currentSession,
                accounts = accounts,
                folders = uiState.folders,
                selectedFolderId = uiState.selectedFolder?.id,
                onFolderSelected = { folder ->
                    viewModel.selectFolder(folder)
                    if (!isExpandedLayout) scope.launch { drawerState.close() }
                },
                onSwitchAccount = { account ->
                    if (!isExpandedLayout) scope.launch { drawerState.close() }
                    onSwitchAccount(account)
                },
                onCompose = onComposeClick,
                onQueue = {
                    if (!isExpandedLayout) scope.launch { drawerState.close() }
                    onOutboxClick()
                },
                onSettings = onSettingsClick,
            )
        } else {
            MailNavigationContent(
                accounts = accounts,
                activeAccountEmail = activeAccountEmail,
                folders = uiState.folders,
                selectedFolder = uiState.selectedFolder,
                pendingQueueCount = uiState.pendingQueueCount,
                queueAttentionCount = uiState.queueAttentionCount,
                onComposeClick = onComposeClick,
                onAddAccountClick = {
                    if (!isExpandedLayout) scope.launch { drawerState.close() }
                    onAddAccountClick()
                },
                onSwitchAccount = { account ->
                    if (!isExpandedLayout) scope.launch { drawerState.close() }
                    onSwitchAccount(account)
                },
                onOutboxClick = {
                    if (!isExpandedLayout) scope.launch { drawerState.close() }
                    onOutboxClick()
                },
                onSettingsClick = onSettingsClick,
                onFolderSelected = { folder ->
                    viewModel.selectFolder(folder)
                    if (!isExpandedLayout) scope.launch { drawerState.close() }
                }
            )
        }
    }

    val screenContent: @Composable () -> Unit = {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                if (uiState.selectedMessageIds.isEmpty()) {
                    ExtendedFloatingActionButton(
                        onClick = onComposeClick,
                        icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        text = { Text("Написать") },
                    )
                }
            },
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
                            IconButton(onClick = { viewModel.markSelectedReadStatus(isUnread = false) }) {
                                Icon(Icons.Default.MarkEmailRead, contentDescription = "Отметить прочитанным")
                            }
                            IconButton(onClick = { viewModel.markSelectedReadStatus(isUnread = true) }) {
                                Icon(Icons.Default.MarkEmailUnread, contentDescription = "Отметить непрочитанным")
                            }
                            IconButton(onClick = { viewModel.archiveSelected() }) {
                                Icon(Icons.Default.Archive, contentDescription = "Архивировать")
                            }
                            IconButton(onClick = { viewModel.deleteSelected() }) {
                                Icon(Icons.Default.Delete, contentDescription = "Удалить")
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
                pagingItems = pagingItems,
                hiddenMessageIds = uiState.hiddenMessageIds,
                readStatusOverrides = uiState.readStatusOverrides,
                selectedIds = uiState.selectedMessageIds,
                selectedMessageId = uiState.selectedMessageId,
                onMessageClick = { messageId ->
                    if (uiState.selectedMessageIds.isNotEmpty()) {
                        viewModel.toggleMessageSelection(messageId)
                    } else if (isExpandedLayout) {
                        viewModel.selectMessage(messageId)
                    } else {
                        viewModel.updateMessageReadStatus(messageId, isUnread = false)
                        onMessageClick(messageId)
                    }
                },
                onMessageLongClick = { messageId -> viewModel.toggleMessageSelection(messageId) },
                onSwipeArchive = { messageId -> viewModel.archiveMessage(messageId) },
                onSwipeDelete = { messageId -> viewModel.deleteMessageWithUndo(messageId) },
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
                HorizontalDivider(modifier = Modifier.fillMaxSize())
            }
            Row(modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.weight(0.9f)) {
                    screenContent()
                }
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .padding(vertical = 12.dp)
                ) {
                    HorizontalDivider(modifier = Modifier.fillMaxSize())
                }
                Box(modifier = Modifier.weight(1.1f)) {
                    detailPane(uiState.selectedMessageId)
                }
            }
        }
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = { navigationContent() },
        ) {
            screenContent()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MailNavigationContent(
    accounts: List<SavedSession>,
    activeAccountEmail: String,
    folders: List<com.mobilemail.domain.model.Folder>,
    selectedFolder: com.mobilemail.domain.model.Folder?,
    pendingQueueCount: Int,
    queueAttentionCount: Int,
    onComposeClick: () -> Unit,
    onAddAccountClick: () -> Unit,
    onSwitchAccount: (SavedSession) -> Unit,
    onOutboxClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onFolderSelected: (com.mobilemail.domain.model.Folder) -> Unit
) {
    var showAccountMenu by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .clickable { showAccountMenu = true }
        ) {
            Text(
                text = accounts.firstOrNull { it.email == activeAccountEmail }?.email ?: "MobileMail",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Переключить аккаунт",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        DropdownMenu(
            expanded = showAccountMenu,
            onDismissRequest = { showAccountMenu = false }
        ) {
            accounts.forEach { account ->
                DropdownMenuItem(
                    text = { Text(account.email) },
                    onClick = {
                        showAccountMenu = false
                        onSwitchAccount(account)
                    }
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Добавить аккаунт") },
                onClick = {
                    showAccountMenu = false
                    onAddAccountClick()
                }
            )
        }

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
            icon = { Icon(Icons.Default.Schedule, contentDescription = null) },
            label = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Очередь")
                    when {
                        queueAttentionCount > 0 -> Badge(containerColor = MaterialTheme.colorScheme.error) {
                            Text(queueAttentionCount.toString(), style = MaterialTheme.typography.labelSmall)
                        }
                        pendingQueueCount > 0 -> Badge(containerColor = MaterialTheme.colorScheme.primary) {
                            Text(pendingQueueCount.toString(), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            },
            selected = false,
            onClick = onOutboxClick,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = { Text("Настройки") },
            selected = false,
            onClick = onSettingsClick,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

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
    folders: List<com.mobilemail.domain.model.Folder>,
    selectedFolder: com.mobilemail.domain.model.Folder?,
    onFolderSelected: (com.mobilemail.domain.model.Folder) -> Unit,
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
    val locale = LocalConfiguration.current.locales[0]
    val customFolders = folders
        .filter { it.role !in defaultOrder }
        .sortedBy { it.name.lowercase(locale) }

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
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
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
    folder: com.mobilemail.domain.model.Folder,
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
        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        tonalElevation = if (isSelected) 1.dp else 0.dp
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
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f)
            )
            if (folder.unreadCount > 0) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Text(folder.unreadCount.toString(), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun MessagesList(
    pagingItems: androidx.paging.compose.LazyPagingItems<MessageListItem>,
    hiddenMessageIds: Set<String>,
    readStatusOverrides: Map<String, Boolean>,
    selectedIds: Set<String>,
    selectedMessageId: String?,
    onMessageClick: (String) -> Unit,
    onMessageLongClick: (String) -> Unit,
    onSwipeArchive: (String) -> Unit,
    onSwipeDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val refreshState = pagingItems.loadState.refresh
    val appendState = pagingItems.loadState.append

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        if (refreshState is LoadState.Loading && pagingItems.itemCount == 0) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        } else if (refreshState is LoadState.Error && pagingItems.itemCount == 0) {
            val message = refreshState.error.message ?: "Не удалось загрузить письма"
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Button(onClick = { pagingItems.retry() }) {
                    Text("Повторить")
                }
            }
        } else if (pagingItems.itemCount == 0 && refreshState !is LoadState.Loading) {
            Text(
                text = "Нет писем",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                items(
                    count = pagingItems.itemCount,
                    key = pagingItems.itemKey { it.id }
                ) { index ->
                    val message = pagingItems[index] ?: return@items
                    if (message.id !in hiddenMessageIds) {
                        val displayedMessage = readStatusOverrides[message.id]?.let { isUnread ->
                            message.copy(flags = message.flags.copy(unread = isUnread))
                        } ?: message
                        if (selectedIds.isNotEmpty()) {
                            // При активном выборе — только EmailListItem с combinedClickable
                            MessageItem(
                                message = displayedMessage,
                                isSelected = selectedIds.contains(message.id),
                                isActive = selectedMessageId == message.id,
                                onClick = { onMessageClick(message.id) },
                                onLongClick = { onMessageLongClick(message.id) },
                                onSwipeArchive = { onSwipeArchive(message.id) },
                                onSwipeDelete = { onSwipeDelete(message.id) }
                            )
                        } else {
                            SwipeableEmailItem(
                                message = displayedMessage,
                                onClick = { onMessageClick(message.id) },
                                onLongClick = { onMessageLongClick(message.id) },
                                onArchive = { onSwipeArchive(message.id) },
                                onDelete = { onSwipeDelete(message.id) },
                            )
                        }
                    }
                }

                if (appendState is LoadState.Loading) {
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
                if (appendState is LoadState.Error) {
                    item {
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = appendState.error.message ?: "Ошибка загрузки дополнительных писем",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { pagingItems.retry() }) {
                                    Text("Повторить")
                                }
                            }
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
    isActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSwipeArchive: () -> Unit,
    onSwipeDelete: () -> Unit
) {
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) { SimpleDateFormat("dd.MM.yyyy HH:mm", locale) }
    val isUnread = message.flags.unread
    val haptics = LocalHapticFeedback.current
    val swipeThresholdPx = with(LocalDensity.current) { 112.dp.toPx() }
    val maxSwipePx = swipeThresholdPx * 1.7f
    var offsetX by remember(message.id) { mutableFloatStateOf(0f) }
    var thresholdDirection by remember(message.id) { mutableIntStateOf(0) }
    val swipeProgress = min(abs(offsetX) / swipeThresholdPx, 1f)

    val backgroundColor = when {
        offsetX > 0f -> MaterialTheme.colorScheme.secondaryContainer
        offsetX < 0f -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val backgroundAlignment = when {
        offsetX > 0f -> Alignment.CenterStart
        offsetX < 0f -> Alignment.CenterEnd
        else -> Alignment.Center
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(backgroundColor.copy(alpha = 0.35f + (0.45f * swipeProgress)))
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 20.dp),
            contentAlignment = backgroundAlignment
        ) {
            if (offsetX != 0f) {
                Icon(
                    imageVector = if (offsetX > 0f) Icons.Default.Archive else Icons.Default.Delete,
                    contentDescription = if (offsetX > 0f) "Архивировать" else "Удалить",
                    tint = if (offsetX > 0f) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    stateDescription = buildString {
                        append(if (isUnread) "Непрочитанное" else "Прочитанное")
                        if (message.flags.starred) append(", в избранном")
                        if (message.flags.hasAttachments) append(", с вложением")
                        if (isActive) append(", открыто")
                        if (isSelected) append(", выбрано")
                    }
                }
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .pointerInput(message.id, isSelected) {
                    if (isSelected) return@pointerInput
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            offsetX = (offsetX + dragAmount).coerceIn(-maxSwipePx, maxSwipePx)
                            val currentDirection = when {
                                offsetX >= swipeThresholdPx -> 1
                                offsetX <= -swipeThresholdPx -> -1
                                else -> 0
                            }
                            if (currentDirection != 0 && currentDirection != thresholdDirection) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            thresholdDirection = currentDirection
                        },
                        onDragEnd = {
                            when {
                                offsetX >= swipeThresholdPx -> onSwipeArchive()
                                offsetX <= -swipeThresholdPx -> onSwipeDelete()
                            }
                            offsetX = 0f
                            thresholdDirection = 0
                        },
                        onDragCancel = {
                            offsetX = 0f
                            thresholdDirection = 0
                        }
                    )
                },
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (isUnread) 4.dp else 2.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isSelected -> MaterialTheme.colorScheme.secondaryContainer
                    isActive -> MaterialTheme.colorScheme.secondaryContainer
                    isUnread -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                    else -> MaterialTheme.colorScheme.surface
                }
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                if (isUnread && !isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(4.dp)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
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
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            val senderWeight = if (isUnread) FontWeight.Bold else FontWeight.SemiBold
                            Text(
                                text = message.from.name ?: message.from.email,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = senderWeight),
                                color = if (isUnread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = dateFormat.format(message.date),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isUnread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    val subjectWeight = if (isUnread) FontWeight.Bold else FontWeight.SemiBold
                    Text(
                        text = message.subject,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = subjectWeight),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (message.snippet.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = message.snippet,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isUnread) FontWeight.Medium else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (message.flags.hasAttachments) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DriveFileMove,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Есть вложения",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                }
            }
        }
    }
}
