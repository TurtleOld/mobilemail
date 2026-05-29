package com.mobilemail.ui.messagedetail

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mobilemail.data.jmap.JmapApi
import com.mobilemail.data.jmap.MailClientFactory
import com.mobilemail.data.model.Folder
import com.mobilemail.data.model.FolderRole
import com.mobilemail.data.model.MessageDetail
import com.mobilemail.data.model.MessageListItem
import com.mobilemail.data.repository.AttachmentRepository
import com.mobilemail.data.repository.MailRepository
import com.mobilemail.data.repository.MessageActionsRepository
import com.mobilemail.data.sync.OfflineQueueManager
import com.mobilemail.ui.common.AppError
import com.mobilemail.ui.common.ErrorMapper
import com.mobilemail.ui.common.NotificationState
import com.mobilemail.data.common.fold
import com.mobilemail.util.FileManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class MessageDetailUiState(
    val message: MessageDetail? = null,
    val threadMessages: List<MessageListItem> = emptyList(),
    val threadDetails: List<MessageDetail> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val isLoading: Boolean = false,
    val error: AppError? = null,
    val notification: NotificationState = NotificationState.None
)

class MessageDetailViewModel(
    application: Application,
    private val server: String,
    private val email: String,
    private val accountId: String,
    private val messageId: String
) : AndroidViewModel(application) {
    private data class PendingReadStatusUpdate(
        val messageId: String,
        val isUnread: Boolean
    )

    private data class PendingDetailAction(
        val id: String,
        val job: Job,
        val commit: suspend () -> com.mobilemail.data.common.Result<Boolean>,
        val onCommitted: () -> Unit,
        val onQueued: suspend () -> Unit
    )

    private val _uiState = MutableStateFlow(MessageDetailUiState())
    val uiState: StateFlow<MessageDetailUiState> = _uiState
    private val app: Application = getApplication()

    private val jmapClient: JmapApi = buildJmapClient()
    private val repository = MailRepository(jmapClient)
    private val messageActionsRepository = MessageActionsRepository(jmapClient)
    private val attachmentRepository = AttachmentRepository(jmapClient)
    
    private var onReadStatusChangedCallback: ((String, Boolean) -> Unit)? = null
    private var pendingDetailAction: PendingDetailAction? = null
    private var pendingReadStatusUpdate: PendingReadStatusUpdate? = null

    init {
        Log.d("MessageDetailViewModel", "Инициализация: messageId=$messageId, accountId=$accountId")
        loadFolders()
        loadMessage()
    }

    private fun buildJmapClient(): JmapApi = MailClientFactory.create(
        application = getApplication(),
        server = server,
        email = email,
        accountId = accountId
    )
    
    fun setOnReadStatusChanged(callback: ((String, Boolean) -> Unit)?) {
        onReadStatusChangedCallback = callback
        if (callback != null) {
            pendingReadStatusUpdate?.let { pendingUpdate ->
                callback(pendingUpdate.messageId, pendingUpdate.isUnread)
                pendingReadStatusUpdate = null
            }
        }
    }

    private fun loadFolders() {
        viewModelScope.launch {
            repository.getFolders().fold(
                onError = { e ->
                    _uiState.value = _uiState.value.copy(error = ErrorMapper.mapException(e))
                },
                onSuccess = { folders ->
                    _uiState.value = _uiState.value.copy(folders = folders)
                }
            )
        }
    }

    private fun loadMessage() {
        viewModelScope.launch {
            Log.d("MessageDetailViewModel", "Начало загрузки письма: messageId=$messageId")
            _uiState.value = _uiState.value.copy(isLoading = true)
            repository.getMessage(messageId).fold(
                onError = { e ->
                    Log.e("MessageDetailViewModel", "Ошибка загрузки письма", e)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = ErrorMapper.mapException(e)
                    )
                },
                onSuccess = { message ->
                    Log.d("MessageDetailViewModel", "Письмо получено успешно, unread=${message.flags.unread}")
                    
                    // Автоматически помечаем как прочитанное, если оно непрочитанное
                    // Делаем это ДО обновления UI, чтобы статус сразу был правильным
                    if (message.flags.unread) {
                        Log.d("MessageDetailViewModel", "Письмо непрочитанное, автоматически помечаем как прочитанное")
                        markAsReadSilently(message)
                    } else {
                        Log.d("MessageDetailViewModel", "Письмо уже прочитанное, статус не изменяем")
                    }
                    
                    // Обновляем UI с правильным статусом
                    _uiState.value = _uiState.value.copy(
                        message = if (message.flags.unread) {
                            // Если было непрочитанным, сразу обновляем статус в UI
                            message.copy(flags = message.flags.copy(unread = false))
                        } else {
                            message
                        },
                        isLoading = false
                    )
                    loadThreadMessages(message.threadId)
                }
            )
        }
    }

    private fun loadThreadMessages(threadId: String) {
        viewModelScope.launch {
            repository.getThreadMessages(threadId).fold(
                onError = { e ->
                    Log.e("MessageDetailViewModel", "Ошибка загрузки переписки", e)
                },
                onSuccess = { threadMessages ->
                    _uiState.value = _uiState.value.copy(threadMessages = threadMessages)
                }
            )
            repository.getThreadDetails(threadId).fold(
                onError = { e ->
                    Log.e("MessageDetailViewModel", "Ошибка загрузки полной переписки", e)
                },
                onSuccess = { threadDetails ->
                    _uiState.value = _uiState.value.copy(threadDetails = threadDetails)
                }
            )
        }
    }
    
    private fun markAsReadSilently(initialMessage: com.mobilemail.data.model.MessageDetail) {
        viewModelScope.launch {
            Log.d("MessageDetailViewModel", "Начало автоматической пометки как прочитанного")
            messageActionsRepository.markAsRead(messageId, true).fold(
                onError = { e ->
                    Log.e("MessageDetailViewModel", "Ошибка автоматической пометки как прочитанного", e)
                    if (OfflineQueueManager.shouldQueue(e)) {
                        viewModelScope.launch {
                            OfflineQueueManager.enqueueMarkRead(app, server, email, accountId, messageId, true)
                        }
                        notifyReadStatusChanged(messageId, isUnread = false)
                    } else {
                        _uiState.value = _uiState.value.copy(
                            message = initialMessage
                        )
                    }
                },
                onSuccess = {
                    Log.d("MessageDetailViewModel", "Письмо автоматически помечено как прочитанное на сервере")
                    val currentMessage = _uiState.value.message ?: initialMessage
                    _uiState.value = _uiState.value.copy(
                        message = currentMessage.copy(
                            flags = currentMessage.flags.copy(unread = false)
                        )
                    )
                    Log.d("MessageDetailViewModel", "UI обновлен, статус: unread=false")
                    Log.d("MessageDetailViewModel", "Отправка обновления статуса прочитанности")
                    notifyReadStatusChanged(messageId, isUnread = false)
                }
            )
        }
    }

    private fun notifyReadStatusChanged(messageId: String, isUnread: Boolean) {
        val callback = onReadStatusChangedCallback
        if (callback != null) {
            callback(messageId, isUnread)
        } else {
            pendingReadStatusUpdate = PendingReadStatusUpdate(messageId, isUnread)
        }
    }

    fun deleteMessage(onSuccess: () -> Unit, onMessageDeleted: ((String) -> Unit)? = null) {
        _uiState.value.message ?: return
        viewModelScope.launch {
            var navigated = false
            val deleteRequest = launch {
                messageActionsRepository.deleteMessage(messageId).fold(
                    onError = { e ->
                        if (OfflineQueueManager.shouldQueue(e)) {
                            OfflineQueueManager.enqueueDelete(app, server, email, accountId, messageId)
                            if (!navigated) {
                                navigated = true
                                onMessageDeleted?.invoke(messageId)
                                onSuccess()
                            }
                        } else if (!navigated) {
                            _uiState.value = _uiState.value.copy(
                                notification = NotificationState.Snackbar(
                                    message = "Не удалось удалить письмо: ${ErrorMapper.mapException(e).getUserMessage()}",
                                    duration = com.mobilemail.ui.common.SnackbarDuration.Long
                                )
                            )
                        }
                    },
                    onSuccess = {
                        if (!navigated) {
                            navigated = true
                            onMessageDeleted?.invoke(messageId)
                            onSuccess()
                        }
                    }
                )
            }

            // Не блокируем экран детали, если сеть отвечает медленно.
            delay(400)
            if (!navigated && deleteRequest.isActive) {
                navigated = true
                onMessageDeleted?.invoke(messageId)
                onSuccess()
            }
        }
    }

    fun toggleReadStatus(onReadStatusChanged: ((String, Boolean) -> Unit)? = null) {
        val currentMessage = _uiState.value.message ?: return
        val newReadStatus = !currentMessage.flags.unread
        
        viewModelScope.launch {
            messageActionsRepository.markAsRead(messageId, !newReadStatus).fold(
                onError = { e ->
                    Log.e("MessageDetailViewModel", "Ошибка изменения статуса прочитанности", e)
                    if (OfflineQueueManager.shouldQueue(e)) {
                        viewModelScope.launch {
                            OfflineQueueManager.enqueueMarkRead(app, server, email, accountId, messageId, !newReadStatus)
                        }
                        _uiState.value = _uiState.value.copy(
                            message = currentMessage.copy(flags = currentMessage.flags.copy(unread = newReadStatus)),
                            notification = NotificationState.Snackbar(
                                message = "Нет сети. Изменение прочитанности поставлено в очередь.",
                                duration = com.mobilemail.ui.common.SnackbarDuration.Long
                            )
                        )
                        onReadStatusChanged?.invoke(messageId, newReadStatus)
                    } else {
                        _uiState.value = _uiState.value.copy(
                            notification = NotificationState.Snackbar(
                                message = "Не удалось изменить статус: ${ErrorMapper.mapException(e).getUserMessage()}",
                                duration = com.mobilemail.ui.common.SnackbarDuration.Long
                            )
                        )
                    }
                },
                onSuccess = {
                    Log.d("MessageDetailViewModel", "Статус прочитанности изменен: $newReadStatus")
                    // Обновляем локальное состояние
                    _uiState.value = _uiState.value.copy(
                        message = currentMessage.copy(
                            flags = currentMessage.flags.copy(unread = newReadStatus)
                        ),
                        notification = NotificationState.Snackbar(
                            message = if (newReadStatus) "Помечено как непрочитанное" else "Помечено как прочитанное",
                            duration = com.mobilemail.ui.common.SnackbarDuration.Short
                        )
                    )
                    // Уведомляем о изменении статуса для обновления счетчика
                    Log.d("MessageDetailViewModel", "Вызов onReadStatusChanged: messageId=$messageId, isUnread=$newReadStatus")
                    onReadStatusChanged?.invoke(messageId, newReadStatus)
                }
            )
        }
    }

    fun toggleStarred() {
        val currentMessage = _uiState.value.message ?: return
        val newStarredStatus = !currentMessage.flags.starred
        
        viewModelScope.launch {
            messageActionsRepository.toggleStarred(messageId, newStarredStatus).fold(
                onError = { e ->
                    Log.e("MessageDetailViewModel", "Ошибка изменения статуса звездочки", e)
                    if (OfflineQueueManager.shouldQueue(e)) {
                        viewModelScope.launch {
                            OfflineQueueManager.enqueueToggleStar(app, server, email, accountId, messageId, newStarredStatus)
                        }
                        _uiState.value = _uiState.value.copy(
                            message = currentMessage.copy(flags = currentMessage.flags.copy(starred = newStarredStatus)),
                            notification = NotificationState.Snackbar(
                                message = "Нет сети. Изменение избранного поставлено в очередь.",
                                duration = com.mobilemail.ui.common.SnackbarDuration.Long
                            )
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            notification = NotificationState.Snackbar(
                                message = "Не удалось изменить статус: ${ErrorMapper.mapException(e).getUserMessage()}",
                                duration = com.mobilemail.ui.common.SnackbarDuration.Long
                            )
                        )
                    }
                },
                onSuccess = {
                    Log.d("MessageDetailViewModel", "Статус звездочки изменен: $newStarredStatus")
                    // Обновляем локальное состояние
                    _uiState.value = _uiState.value.copy(
                        message = currentMessage.copy(
                            flags = currentMessage.flags.copy(starred = newStarredStatus)
                        ),
                        notification = NotificationState.Snackbar(
                            message = if (newStarredStatus) "Добавлено в избранное" else "Удалено из избранного",
                            duration = com.mobilemail.ui.common.SnackbarDuration.Short
                        )
                    )
                }
            )
        }
    }

    fun archiveMessage(onSuccess: () -> Unit, onMessageRemoved: ((String) -> Unit)? = null) {
        moveMessageToRole(FolderRole.ARCHIVE, onSuccess, onMessageRemoved)
    }

    fun reportSpam(onSuccess: () -> Unit, onMessageRemoved: ((String) -> Unit)? = null) {
        moveMessageToRole(FolderRole.SPAM, onSuccess, onMessageRemoved)
    }

    fun moveMessage(
        toFolderId: String,
        onSuccess: () -> Unit,
        onMessageRemoved: ((String) -> Unit)? = null
    ) {
        val currentMessage = _uiState.value.message ?: return
        val sourceFolderId = currentMessage.mailboxIds.firstOrNull()
            ?: _uiState.value.folders.firstOrNull { it.role == FolderRole.INBOX }?.id
            ?: run {
                _uiState.value = _uiState.value.copy(
                    notification = NotificationState.Snackbar(
                        message = "Не удалось определить исходную папку",
                        duration = com.mobilemail.ui.common.SnackbarDuration.Short
                    )
                )
                return
            }

        if (sourceFolderId == toFolderId) {
            _uiState.value = _uiState.value.copy(
                notification = NotificationState.Snackbar(
                    message = "Письмо уже находится в выбранной папке",
                    duration = com.mobilemail.ui.common.SnackbarDuration.Short
                )
            )
            return
        }

        val destinationFolder = _uiState.value.folders.firstOrNull { it.id == toFolderId }
        scheduleDeferredAction(
            pendingMessage = "Письмо перемещается в ${destinationFolder?.name ?: "другую папку"}",
            commitAction = { messageActionsRepository.moveMessage(messageId, sourceFolderId, toFolderId) },
            onCommitted = {
                onMessageRemoved?.invoke(messageId)
                onSuccess()
            },
            onQueued = {
                OfflineQueueManager.enqueueMove(app, server, email, accountId, messageId, sourceFolderId, toFolderId)
            }
        )
    }

    private fun moveMessageToRole(
        role: FolderRole,
        onSuccess: () -> Unit,
        onMessageRemoved: ((String) -> Unit)? = null
    ) {
        val destination = _uiState.value.folders.firstOrNull { it.role == role }
        if (destination == null) {
            _uiState.value = _uiState.value.copy(
                notification = NotificationState.Snackbar(
                    message = when (role) {
                        FolderRole.ARCHIVE -> "Папка Архив недоступна"
                        FolderRole.SPAM -> "Папка Спам недоступна"
                        else -> "Целевая папка недоступна"
                    },
                    duration = com.mobilemail.ui.common.SnackbarDuration.Short
                )
            )
            return
        }
        moveMessage(destination.id, onSuccess, onMessageRemoved)
    }

    fun clearNotification() {
        _uiState.value = _uiState.value.copy(notification = NotificationState.None)
    }

    private fun scheduleDeferredAction(
        pendingMessage: String,
        commitAction: suspend () -> com.mobilemail.data.common.Result<Boolean>,
        onCommitted: () -> Unit,
        onQueued: suspend () -> Unit
    ) {
        val existingAction = pendingDetailAction
        if (existingAction != null) {
            viewModelScope.launch {
                finalizePendingDetailAction(existingAction)
                scheduleDeferredAction(pendingMessage, commitAction, onCommitted, onQueued)
            }
            return
        }

        val actionId = UUID.randomUUID().toString()
        val job = viewModelScope.launch {
            delay(5000)
            pendingDetailAction?.takeIf { it.id == actionId }?.let { finalizePendingDetailAction(it) }
        }

        pendingDetailAction = PendingDetailAction(
            id = actionId,
            job = job,
            commit = commitAction,
            onCommitted = onCommitted,
            onQueued = onQueued
        )
        _uiState.value = _uiState.value.copy(
            notification = NotificationState.Snackbar(
                message = pendingMessage,
                actionLabel = "Отменить",
                onAction = { undoPendingDetailAction(actionId) },
                duration = com.mobilemail.ui.common.SnackbarDuration.Long
            )
        )
    }

    private suspend fun finalizePendingDetailAction(action: PendingDetailAction) {
        pendingDetailAction = null
        action.commit().fold(
            onError = { e ->
                if (OfflineQueueManager.shouldQueue(e)) {
                    viewModelScope.launch {
                        action.onQueued()
                    }
                    _uiState.value = _uiState.value.copy(
                        notification = NotificationState.Snackbar(
                            message = "Нет сети. Действие поставлено в очередь и будет повторено автоматически.",
                            duration = com.mobilemail.ui.common.SnackbarDuration.Long
                        )
                    )
                    action.onCommitted()
                } else {
                    _uiState.value = _uiState.value.copy(
                        notification = NotificationState.Snackbar(
                            message = "Не удалось синхронизировать изменения: ${ErrorMapper.mapException(e).getUserMessage()}",
                            duration = com.mobilemail.ui.common.SnackbarDuration.Long
                        )
                    )
                }
            },
            onSuccess = {
                action.onCommitted()
            }
        )
    }

    private fun undoPendingDetailAction(actionId: String) {
        val action = pendingDetailAction?.takeIf { it.id == actionId } ?: return
        action.job.cancel()
        pendingDetailAction = null
        _uiState.value = _uiState.value.copy(
            notification = NotificationState.Snackbar(
                message = "Действие отменено",
                duration = com.mobilemail.ui.common.SnackbarDuration.Short
            )
        )
    }

    fun openAttachment(attachmentId: String, filename: String, mimeType: String = "application/octet-stream") {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                notification = NotificationState.Snackbar(
                    message = "Открытие вложения: $filename",
                    duration = com.mobilemail.ui.common.SnackbarDuration.Short
                )
            )
            
            attachmentRepository.downloadAttachment(attachmentId).fold(
                onError = { e ->
                    Log.e("MessageDetailViewModel", "Ошибка загрузки вложения", e)
                    _uiState.value = _uiState.value.copy(
                        notification = NotificationState.Snackbar(
                            message = "Не удалось загрузить вложение: ${ErrorMapper.mapException(e).getUserMessage()}",
                            duration = com.mobilemail.ui.common.SnackbarDuration.Long
                        )
                    )
                },
                onSuccess = { data ->
                    Log.d("MessageDetailViewModel", "Вложение загружено: ${data.size} байт")
                    
                    viewModelScope.launch {
                        FileManager.saveToCache(getApplication(), filename, data, mimeType).fold(
                            onError = { e ->
                                Log.e("MessageDetailViewModel", "Ошибка создания временного файла", e)
                                _uiState.value = _uiState.value.copy(
                                    notification = NotificationState.Snackbar(
                                        message = "Не удалось открыть файл: ${ErrorMapper.mapException(e).getUserMessage()}",
                                        duration = com.mobilemail.ui.common.SnackbarDuration.Long
                                    )
                                )
                            },
                            onSuccess = { uri ->
                                openFile(uri, mimeType)
                                _uiState.value = _uiState.value.copy(
                                    notification = NotificationState.None
                                )
                            }
                        )
                    }
                }
            )
        }
    }

    fun downloadAttachment(attachmentId: String, filename: String, mimeType: String = "application/octet-stream") {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                notification = NotificationState.Snackbar(
                    message = "Загрузка вложения: $filename",
                    duration = com.mobilemail.ui.common.SnackbarDuration.Short
                )
            )
            
            attachmentRepository.downloadAttachment(attachmentId).fold(
                onError = { e ->
                    Log.e("MessageDetailViewModel", "Ошибка загрузки вложения", e)
                    _uiState.value = _uiState.value.copy(
                        notification = NotificationState.Snackbar(
                            message = "Не удалось загрузить вложение: ${ErrorMapper.mapException(e).getUserMessage()}",
                            duration = com.mobilemail.ui.common.SnackbarDuration.Long
                        )
                    )
                },
                onSuccess = { data ->
                    Log.d("MessageDetailViewModel", "Вложение загружено: ${data.size} байт")
                    
                    viewModelScope.launch {
                        FileManager.saveToDownloads(getApplication(), filename, data, mimeType).fold(
                            onError = { e ->
                                Log.e("MessageDetailViewModel", "Ошибка сохранения файла", e)
                                _uiState.value = _uiState.value.copy(
                                    notification = NotificationState.Snackbar(
                                        message = "Не удалось сохранить файл: ${ErrorMapper.mapException(e).getUserMessage()}",
                                        duration = com.mobilemail.ui.common.SnackbarDuration.Long
                                    )
                                )
                            },
                            onSuccess = { uri ->
                                val filePath = FileManager.getFilePath(getApplication(), uri)
                                Log.d("MessageDetailViewModel", "Файл сохранен: $filePath")
                                _uiState.value = _uiState.value.copy(
                                    notification = NotificationState.Snackbar(
                                        message = "Вложение загружено: $filename (${formatFileSize(data.size.toLong())})",
                                        actionLabel = "Открыть",
                                        onAction = {
                                            openFile(uri, mimeType)
                                        },
                                        duration = com.mobilemail.ui.common.SnackbarDuration.Long
                                    )
                                )
                            }
                        )
                    }
                }
            )
        }
    }
    
    private fun openFile(uri: Uri, mimeType: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            getApplication<Application>().startActivity(intent)
        } catch (e: Exception) {
            Log.e("MessageDetailViewModel", "Ошибка открытия файла", e)
            _uiState.value = _uiState.value.copy(
                notification = NotificationState.Snackbar(
                    message = "Не удалось открыть файл",
                    duration = com.mobilemail.ui.common.SnackbarDuration.Short
                )
            )
        }
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024L -> "$bytes Б"
            bytes < 1024L * 1024L -> "${bytes / 1024L} КБ"
            else -> "${bytes / (1024L * 1024L)} МБ"
        }
    }
}
