package com.mobilemail.ui.messagedetail

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mobilemail.data.jmap.JmapClient
import com.mobilemail.data.model.MessageDetail
import com.mobilemail.data.repository.AttachmentRepository
import com.mobilemail.data.repository.MailRepository
import com.mobilemail.data.repository.MessageActionsRepository
import com.mobilemail.ui.common.AppError
import com.mobilemail.ui.common.ErrorMapper
import com.mobilemail.ui.common.NotificationState
import com.mobilemail.data.common.fold
import com.mobilemail.util.FileManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class MessageDetailUiState(
    val message: MessageDetail? = null,
    val isLoading: Boolean = false,
    val error: AppError? = null,
    val notification: NotificationState = NotificationState.None
)

class MessageDetailViewModel(
    application: Application,
    private val server: String,
    private val email: String,
    private val password: String,
    private val accountId: String,
    private val messageId: String
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(MessageDetailUiState())
    val uiState: StateFlow<MessageDetailUiState> = _uiState

    private val jmapClient = JmapClient.getOrCreate(server, email, password, accountId)
    private val repository = MailRepository(jmapClient)
    private val messageActionsRepository = MessageActionsRepository(jmapClient)
    private val attachmentRepository = AttachmentRepository(jmapClient)
    
    private var onReadStatusChangedCallback: ((String, Boolean) -> Unit)? = null

    init {
        Log.d("MessageDetailViewModel", "Инициализация: messageId=$messageId, accountId=$accountId")
        loadMessage()
    }
    
    fun setOnReadStatusChanged(callback: ((String, Boolean) -> Unit)?) {
        onReadStatusChangedCallback = callback
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
                    // В случае ошибки возвращаем исходный статус
                    _uiState.value = _uiState.value.copy(
                        message = initialMessage
                    )
                },
                onSuccess = {
                    Log.d("MessageDetailViewModel", "Письмо автоматически помечено как прочитанное на сервере")
                    // Обновляем UI с прочитанным статусом
                    val currentMessage = _uiState.value.message
                    if (currentMessage != null) {
                        _uiState.value = _uiState.value.copy(
                            message = currentMessage.copy(
                                flags = currentMessage.flags.copy(unread = false)
                            )
                        )
                        Log.d("MessageDetailViewModel", "UI обновлен, статус: unread=false")
                    }
                    // Уведомляем о изменении статуса для обновления счетчика
                    Log.d("MessageDetailViewModel", "Вызов callback для обновления счетчика")
                    onReadStatusChangedCallback?.invoke(messageId, false)
                }
            )
        }
    }

    fun deleteMessage(onSuccess: () -> Unit, onMessageDeleted: ((String) -> Unit)? = null) {
        val currentMessage = _uiState.value.message ?: return
        viewModelScope.launch {
            messageActionsRepository.deleteMessage(messageId).fold(
                onError = { e ->
                    Log.e("MessageDetailViewModel", "Ошибка удаления письма", e)
                    _uiState.value = _uiState.value.copy(
                        notification = NotificationState.Snackbar(
                            message = "Не удалось удалить письмо: ${ErrorMapper.mapException(e).getUserMessage()}",
                            duration = com.mobilemail.ui.common.SnackbarDuration.Long
                        )
                    )
                },
                onSuccess = {
                    Log.d("MessageDetailViewModel", "Письмо удалено успешно")
                    _uiState.value = _uiState.value.copy(
                        notification = NotificationState.Snackbar(
                            message = "Письмо удалено",
                            duration = com.mobilemail.ui.common.SnackbarDuration.Short
                        )
                    )
                    onMessageDeleted?.invoke(messageId)
                    onSuccess()
                }
            )
        }
    }

    fun toggleReadStatus(onReadStatusChanged: ((String, Boolean) -> Unit)? = null) {
        val currentMessage = _uiState.value.message ?: return
        val newReadStatus = !currentMessage.flags.unread
        
        viewModelScope.launch {
            messageActionsRepository.markAsRead(messageId, !newReadStatus).fold(
                onError = { e ->
                    Log.e("MessageDetailViewModel", "Ошибка изменения статуса прочитанности", e)
                    _uiState.value = _uiState.value.copy(
                        notification = NotificationState.Snackbar(
                            message = "Не удалось изменить статус: ${ErrorMapper.mapException(e).getUserMessage()}",
                            duration = com.mobilemail.ui.common.SnackbarDuration.Long
                        )
                    )
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
                    _uiState.value = _uiState.value.copy(
                        notification = NotificationState.Snackbar(
                            message = "Не удалось изменить статус: ${ErrorMapper.mapException(e).getUserMessage()}",
                            duration = com.mobilemail.ui.common.SnackbarDuration.Long
                        )
                    )
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

    fun clearNotification() {
        _uiState.value = _uiState.value.copy(notification = NotificationState.None)
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
