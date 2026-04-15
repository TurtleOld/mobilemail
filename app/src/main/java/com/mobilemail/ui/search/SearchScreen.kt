package com.mobilemail.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobilemail.data.model.MessageListItem
import com.mobilemail.ui.theme.EmailShapes
import com.mobilemail.ui.theme.EmailTypography
import com.mobilemail.ui.theme.ExtendedTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onMessageClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pagingItems = viewModel.pagedResults.collectAsLazyPagingItems()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val isExpandedLayout = LocalConfiguration.current.screenWidthDp >= 840
    var showFolderDialog by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
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

    // Диалог выбора папки
    if (showFolderDialog) {
        AlertDialog(
            onDismissRequest = { showFolderDialog = false },
            title = { Text("Выберите папку") },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    item {
                        ListItem(
                            headlineContent = { Text("Все папки") },
                            modifier = Modifier.clickable {
                                viewModel.selectFolder(null)
                                showFolderDialog = false
                            },
                            leadingContent = {
                                RadioButton(
                                    selected = uiState.selectedFolder == null,
                                    onClick = {
                                        viewModel.selectFolder(null)
                                        showFolderDialog = false
                                    }
                                )
                            }
                        )
                    }
                    items(uiState.folders, key = { it.id }) { folder ->
                        ListItem(
                            headlineContent = { Text(folder.name) },
                            modifier = Modifier.clickable {
                                viewModel.selectFolder(folder)
                                showFolderDialog = false
                            },
                            leadingContent = {
                                RadioButton(
                                    selected = uiState.selectedFolder?.id == folder.id,
                                    onClick = {
                                        viewModel.selectFolder(folder)
                                        showFolderDialog = false
                                    }
                                )
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFolderDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Поиск") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (uiState.hasActiveFilters) {
                        IconButton(onClick = { viewModel.clearFilters() }) {
                            Icon(Icons.Default.FilterAltOff, contentDescription = "Сбросить фильтры")
                        }
                    }
                    IconButton(onClick = { viewModel.toggleAdvancedFilters() }) {
                        Icon(Icons.Default.Tune, contentDescription = "Расширенные фильтры")
                    }
                    IconButton(
                        onClick = { viewModel.performSearch() },
                        enabled = (uiState.query.isNotBlank() || uiState.hasActiveFilters) && !uiState.isLoading
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Default.Search, contentDescription = "Поиск")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = if (isExpandedLayout) 24.dp else 0.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                tonalElevation = if (isExpandedLayout) 1.dp else 0.dp,
                color = if (isExpandedLayout) ExtendedTheme.colors.chromeMuted else ExtendedTheme.colors.surfaceReading,
                shape = EmailShapes.searchBar
            ) {
                Column(
                    modifier = Modifier.padding(if (isExpandedLayout) 20.dp else 0.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.query,
                        onValueChange = {
                            viewModel.updateQuery(it)
                            if (it.isBlank()) {
                                viewModel.performSearch()
                            }
                        },
                        label = { Text("Поиск писем") },
                        placeholder = { Text("Введите текст для поиска...") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (uiState.query.isNotEmpty()) {
                                IconButton(onClick = {
                                    viewModel.updateQuery("")
                                    viewModel.performSearch()
                                }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Очистить")
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                keyboardController?.hide()
                                viewModel.performSearch()
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SearchSmartFilter.entries.forEach { filter ->
                            val isSelected = when (filter) {
                                SearchSmartFilter.RECENT -> uiState.dateRange == com.mobilemail.data.repository.SearchRepository.DateRange.LAST_7_DAYS
                                SearchSmartFilter.UNREAD -> uiState.unreadOnly
                                SearchSmartFilter.ATTACHMENTS -> uiState.hasAttachments
                                SearchSmartFilter.STARRED -> uiState.starredOnly
                                SearchSmartFilter.IMPORTANT -> uiState.importantOnly
                            }
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.applySmartFilter(filter) },
                                label = { Text(filter.label) }
                            )
                        }
                        FilterChip(
                            selected = uiState.selectedFolder != null,
                            onClick = { showFolderDialog = true },
                            label = {
                                Text(
                                    text = uiState.selectedFolder?.name ?: "Все папки",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                        FilterChip(
                            selected = uiState.unreadOnly,
                            onClick = { viewModel.toggleUnreadOnly() },
                            label = { Text("Непрочитанные") }
                        )
                        FilterChip(
                            selected = uiState.hasAttachments,
                            onClick = { viewModel.toggleHasAttachments() },
                            label = { Text("С вложениями") }
                        )
                    }

                    if (uiState.showAdvancedFilters) {
                        OutlinedTextField(
                            value = uiState.senderQuery,
                            onValueChange = { viewModel.updateSenderQuery(it) },
                            label = { Text("Отправитель") },
                            placeholder = { Text("Имя или email") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DateFilterChip(
                                label = "Любая дата",
                                selected = uiState.dateRange == com.mobilemail.data.repository.SearchRepository.DateRange.ANY,
                                onClick = { viewModel.setDateRange(com.mobilemail.data.repository.SearchRepository.DateRange.ANY) }
                            )
                            DateFilterChip(
                                label = "Сегодня",
                                selected = uiState.dateRange == com.mobilemail.data.repository.SearchRepository.DateRange.TODAY,
                                onClick = { viewModel.setDateRange(com.mobilemail.data.repository.SearchRepository.DateRange.TODAY) }
                            )
                            DateFilterChip(
                                label = "7 дней",
                                selected = uiState.dateRange == com.mobilemail.data.repository.SearchRepository.DateRange.LAST_7_DAYS,
                                onClick = { viewModel.setDateRange(com.mobilemail.data.repository.SearchRepository.DateRange.LAST_7_DAYS) }
                            )
                            DateFilterChip(
                                label = "30 дней",
                                selected = uiState.dateRange == com.mobilemail.data.repository.SearchRepository.DateRange.LAST_30_DAYS,
                                onClick = { viewModel.setDateRange(com.mobilemail.data.repository.SearchRepository.DateRange.LAST_30_DAYS) }
                            )
                            DateFilterChip(
                                label = "Год",
                                selected = uiState.dateRange == com.mobilemail.data.repository.SearchRepository.DateRange.LAST_365_DAYS,
                                onClick = { viewModel.setDateRange(com.mobilemail.data.repository.SearchRepository.DateRange.LAST_365_DAYS) }
                            )
                        }

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = uiState.starredOnly,
                                onClick = { viewModel.toggleStarredOnly() },
                                label = { Text("Избранные") }
                            )
                            FilterChip(
                                selected = uiState.importantOnly,
                                onClick = { viewModel.toggleImportantOnly() },
                                label = { Text("Важные") }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Результаты поиска
            if (pagingItems.loadState.refresh is LoadState.Loading && pagingItems.itemCount == 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (pagingItems.itemCount == 0 && uiState.hasSearched && pagingItems.loadState.refresh !is LoadState.Loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Ничего не найдено",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (pagingItems.itemCount > 0) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(
                        count = pagingItems.itemCount,
                        key = pagingItems.itemKey { it.id }
                    ) { index ->
                        val message = pagingItems[index] ?: return@items
                        MessageItem(
                            message = message,
                            onClick = { onMessageClick(message.id) }
                        )
                    }
                    if (pagingItems.loadState.append is LoadState.Loading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Введите запрос или выберите фильтры",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) }
    )
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
            .semantics {
                role = Role.Button
                stateDescription = buildString {
                    append(if (message.flags.unread) "Непрочитанное" else "Прочитанное")
                    if (message.flags.hasAttachments) append(", с вложением")
                }
            }
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = ExtendedTheme.colors.surfaceReading),
        shape = EmailShapes.emailCard
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
                    if (message.flags.unread) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(ExtendedTheme.colors.unreadBadge)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = message.from.name ?: message.from.email,
                        style = if (message.flags.unread) EmailTypography.emailSenderUnread else EmailTypography.emailSender,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = dateFormat.format(message.date),
                    style = EmailTypography.emailTimestamp,
                    fontWeight = if (message.flags.unread) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (message.flags.unread) ExtendedTheme.colors.unreadBadge else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = message.subject,
                style = EmailTypography.emailSubject,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (message.snippet.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message.snippet,
                    style = EmailTypography.emailPreview,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (message.flags.hasAttachments) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Есть вложения",
                    style = MaterialTheme.typography.labelSmall,
                    color = ExtendedTheme.colors.attachment
                )
            }
        }
    }
}
