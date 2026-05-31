package com.mobilemail.ui.newmessage

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mobilemail.data.common.fold
import com.mobilemail.domain.model.Attachment
import com.mobilemail.domain.model.toData
import com.mobilemail.data.repository.ComposeRepository
import com.mobilemail.data.sync.OfflineAttachmentStorage
import com.mobilemail.data.sync.OfflineQueueManager
import com.mobilemail.ui.common.AppError
import com.mobilemail.ui.common.ErrorMapper
import com.mobilemail.ui.common.FeatureScreenUiState
import com.mobilemail.ui.common.NotificationState
import com.mobilemail.ui.common.SnackbarDuration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ComposeUiState(
    val isSending: Boolean = false,
    val isSavingDraft: Boolean = false,
    val attachments: List<Attachment> = emptyList(),
    val draftId: String? = null,
    override val error: AppError? = null,
    override val notification: NotificationState = NotificationState.None
) : FeatureScreenUiState

class ComposeViewModel(
    application: Application,
    private val server: String,
    private val email: String,
    private val accountId: String,
    private val repository: ComposeRepository
) : AndroidViewModel(application) {
    companion object {
        private const val MAX_ATTACHMENT_SIZE_BYTES = 10 * 1024 * 1024L
    }
    private val _uiState = MutableStateFlow(ComposeUiState())
    val uiState: StateFlow<ComposeUiState> = _uiState

    fun sendMessage(
        to: List<String>,
        subject: String,
        body: String,
        onSuccess: () -> Unit
    ) {
        val recipients = to.map { it.trim() }.filter { it.isNotBlank() }
        val trimmedSubject = subject.trim()
        val trimmedBody = body.trim()
        Log.d(
            "ComposeViewModel",
            "Отправка письма: toCount=${recipients.size}, subject=${trimmedSubject.length}ch, " +
                "body=${trimmedBody.length}ch, attachments=${_uiState.value.attachments.size}, " +
                "draftId=${_uiState.value.draftId}"
        )

        if (!validateSendInputs(recipients, trimmedSubject, trimmedBody)) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true)
            val resolvedAttachments = resolveAttachmentsForSend(_uiState.value.attachments)
            if (resolvedAttachments == null) {
                _uiState.value = _uiState.value.copy(isSending = false)
                return@launch
            }
            dispatchSend(recipients, trimmedSubject, trimmedBody, resolvedAttachments, onSuccess)
        }
    }

    private fun validateSendInputs(recipients: List<String>, trimmedSubject: String, trimmedBody: String): Boolean {
        if (recipients.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                notification = NotificationState.Snackbar(
                    message = "Введите адрес получателя",
                    duration = com.mobilemail.ui.common.SnackbarDuration.Short
                )
            )
            return false
        }
        if (trimmedSubject.isBlank() && trimmedBody.isBlank()) {
            _uiState.value = _uiState.value.copy(
                notification = NotificationState.Snackbar(
                    message = "Письмо пустое",
                    duration = com.mobilemail.ui.common.SnackbarDuration.Short
                )
            )
            return false
        }
        return true
    }

    private suspend fun dispatchSend(
        recipients: List<String>,
        trimmedSubject: String,
        trimmedBody: String,
        resolvedAttachments: List<Attachment>,
        onSuccess: () -> Unit
    ) {
        repository.sendMessage(
            from = email,
            to = recipients,
            subject = trimmedSubject,
            body = trimmedBody,
            attachments = resolvedAttachments,
            draftId = _uiState.value.draftId
        ).fold(
            onError = { e ->
                Log.e("ComposeViewModel", "Ошибка отправки письма", e)
                handleSendError(e, recipients, trimmedSubject, trimmedBody, resolvedAttachments, onSuccess)
            },
            onSuccess = { submissionId ->
                Log.d("ComposeViewModel", "EmailSubmission создан, submissionId=$submissionId")
                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    attachments = emptyList(),
                    draftId = null,
                    notification = NotificationState.Snackbar(
                        message = "Письмо отправлено (submissionId=$submissionId). Проверяем доставку...",
                        duration = SnackbarDuration.Short
                    )
                )
                onSuccess()
                viewModelScope.launch { pollSubmissionStatus(submissionId) }
            }
        )
    }

    private suspend fun handleSendError(
        e: Throwable,
        recipients: List<String>,
        trimmedSubject: String,
        trimmedBody: String,
        resolvedAttachments: List<Attachment>,
        onSuccess: () -> Unit
    ) {
        if (OfflineQueueManager.shouldQueue(e)) {
            viewModelScope.launch {
                OfflineQueueManager.enqueueSend(
                    application = getApplication(),
                    server = server,
                    email = email,
                    accountId = accountId,
                    to = recipients,
                    subject = trimmedSubject,
                    body = trimmedBody,
                    attachments = resolvedAttachments.map { it.toData() },
                    draftId = _uiState.value.draftId
                )
                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    attachments = emptyList(),
                    draftId = null,
                    notification = NotificationState.Snackbar(
                        message = "Нет сети. Письмо поставлено в очередь на отправку.",
                        duration = SnackbarDuration.Long
                    )
                )
                onSuccess()
            }
        } else {
            _uiState.value = _uiState.value.copy(
                isSending = false,
                notification = NotificationState.Snackbar(
                    message = "Не удалось отправить письмо: ${ErrorMapper.mapException(e).getUserMessage()}",
                    duration = SnackbarDuration.Long
                )
            )
        }
    }

    private suspend fun pollSubmissionStatus(submissionId: String) {
        // Пробуем 12 раз с интервалом 5 сек (≈ 1 минута)
        repeat(12) { attempt ->
            if (!viewModelScope.isActive) return
            kotlinx.coroutines.delay(5000)

            repository.getEmailSubmissionStatus(submissionId).fold(
                onError = { e ->
                    Log.w("ComposeViewModel", "EmailSubmission/get недоступен или ошибка статуса", e)
                    if (attempt == 0) {
                        _uiState.value = _uiState.value.copy(
                            notification = NotificationState.Snackbar(
                                message = "Не удалось проверить статус доставки (сервер не поддерживает EmailSubmission/get)",
                                duration = SnackbarDuration.Short
                            )
                        )
                    }
                    return
                },
                onSuccess = { status ->
                    val finalNotification = resolveSubmissionNotification(status)
                    if (finalNotification != null) {
                        _uiState.value = _uiState.value.copy(notification = finalNotification)
                        return
                    }
                }
            )
        }
    }

    fun addAttachment(
        resolver: ContentResolver,
        uri: Uri
    ) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { readAttachmentFromUri(resolver, uri) }
            }

            result.fold(
                onSuccess = { quadruple ->
                    val (name, type, bytes) = quadruple
                    val localPath = quadruple.fourth
                    uploadAttachmentFile(name, type, bytes, localPath)
                },
                onFailure = { e ->
                    Log.e("ComposeViewModel", "Ошибка чтения вложения", e)
                    _uiState.value = _uiState.value.copy(
                        notification = NotificationState.Snackbar(
                            message = e.message ?: "Не удалось добавить вложение",
                            duration = com.mobilemail.ui.common.SnackbarDuration.Short
                        )
                    )
                }
            )
        }
    }

    private fun readAttachmentFromUri(resolver: ContentResolver, uri: Uri): Quadruple<String, String, ByteArray, String?> {
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                } else null
            } ?: "attachment"

        val size = resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE))
                } else null
            }

        if (size != null && size > MAX_ATTACHMENT_SIZE_BYTES) {
            require(false) { "Размер вложения больше 10 МБ" }
        }

        val type = resolver.getType(uri) ?: "application/octet-stream"
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Не удалось прочитать файл")
        require(bytes.size <= MAX_ATTACHMENT_SIZE_BYTES) { "Размер вложения больше 10 МБ" }
        val localPath = OfflineAttachmentStorage.persist(getApplication(), name, bytes)
        return Quadruple(name, type, bytes, localPath)
    }

    private suspend fun uploadAttachmentFile(name: String, type: String, bytes: ByteArray, localPath: String?) {
        _uiState.value = _uiState.value.copy(
            notification = NotificationState.Snackbar(
                message = "Загрузка вложения: $name",
                duration = com.mobilemail.ui.common.SnackbarDuration.Short
            )
        )
        repository.uploadAttachment(bytes, type, name).fold(
            onError = { e ->
                Log.e("ComposeViewModel", "Ошибка загрузки вложения", e)
                handleUploadError(e, name, type, bytes.size.toLong(), localPath)
            },
            onSuccess = { attachment ->
                _uiState.value = _uiState.value.copy(
                    attachments = _uiState.value.attachments + attachment.copy(localFilePath = localPath),
                    notification = NotificationState.Snackbar(
                        message = "Вложение добавлено: ${attachment.filename}",
                        duration = com.mobilemail.ui.common.SnackbarDuration.Short
                    )
                )
            }
        )
    }

    private fun handleUploadError(e: Throwable, name: String, type: String, size: Long, localPath: String?) {
        if (OfflineQueueManager.shouldQueue(e)) {
            _uiState.value = _uiState.value.copy(
                attachments = _uiState.value.attachments + Attachment(
                    id = "local:${System.currentTimeMillis()}:$name",
                    filename = name,
                    mime = type,
                    size = size,
                    localFilePath = localPath,
                    isUploaded = false
                ),
                notification = NotificationState.Snackbar(
                    message = "Нет сети. Вложение сохранено локально и будет загружено при отправке.",
                    duration = com.mobilemail.ui.common.SnackbarDuration.Long
                )
            )
        } else {
            _uiState.value = _uiState.value.copy(
                notification = NotificationState.Snackbar(
                    message = "Не удалось загрузить вложение: ${ErrorMapper.mapException(e).getUserMessage()}",
                    duration = com.mobilemail.ui.common.SnackbarDuration.Long
                )
            )
        }
    }

    fun removeAttachment(attachment: Attachment) {
        OfflineAttachmentStorage.delete(attachment.localFilePath)
        _uiState.value = _uiState.value.copy(
            attachments = _uiState.value.attachments.filterNot { it.id == attachment.id }
        )
    }

    fun saveDraft(to: List<String>, subject: String, body: String) {
        val trimmedSubject = subject.trim()
        val trimmedBody = body.trim()
        if (trimmedSubject.isBlank() && trimmedBody.isBlank() && to.all { it.isBlank() }) {
            return
        }

        Log.d(
            "ComposeViewModel",
            "Сохранение черновика: toCount=${to.count { it.isNotBlank() }}, " +
                "subject=${trimmedSubject.length}ch, body=${trimmedBody.length}ch, " +
                "attachments=${_uiState.value.attachments.size}, draftId=${_uiState.value.draftId}"
        )

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSavingDraft = true)
            repository.saveDraft(
                from = email,
                to = to.map { it.trim() }.filter { it.isNotBlank() },
                subject = trimmedSubject,
                body = trimmedBody,
                attachments = _uiState.value.attachments.filter { it.isUploaded },
                draftId = _uiState.value.draftId
            ).fold(
                onError = { e ->
                    Log.e("ComposeViewModel", "Ошибка сохранения черновика", e)
                    _uiState.value = _uiState.value.copy(isSavingDraft = false)
                },
                onSuccess = { newDraftId ->
                    _uiState.value = _uiState.value.copy(
                        isSavingDraft = false,
                        draftId = newDraftId
                    )
                }
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearNotification() {
        _uiState.value = _uiState.value.copy(notification = NotificationState.None)
    }

    private fun resolveSubmissionNotification(status: com.mobilemail.domain.model.EmailSubmissionStatus): NotificationState.Snackbar? {
        val text = status.lastStatusText
        if (!text.isNullOrBlank()) {
            return NotificationState.Snackbar(message = "Статус доставки: $text", duration = SnackbarDuration.Long)
        }
        if (status.failed == true) {
            return NotificationState.Snackbar(message = "Доставка письма не удалась", duration = SnackbarDuration.Long)
        }
        if (status.delivered == true) {
            return NotificationState.Snackbar(message = "Письмо доставлено", duration = SnackbarDuration.Short)
        }
        return null
    }

    private suspend fun resolveAttachmentsForSend(attachments: List<Attachment>): List<Attachment>? {
        val resolved = mutableListOf<Attachment>()
        for (attachment in attachments) {
            if (attachment.isUploaded) {
                resolved += attachment
                continue
            }

            val localPath = attachment.localFilePath
            if (localPath.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(
                    notification = NotificationState.Snackbar(
                        message = "Не удалось подготовить вложение ${attachment.filename}",
                        duration = SnackbarDuration.Long
                    )
                )
                return null
            }

            val bytes = withContext(Dispatchers.IO) { OfflineAttachmentStorage.read(localPath) }
            var uploadedAttachment: Attachment? = null
            repository.uploadAttachment(bytes, attachment.mime, attachment.filename).fold(
                onError = { e ->
                    if (OfflineQueueManager.shouldQueue(e)) {
                        uploadedAttachment = attachment
                    } else {
                        _uiState.value = _uiState.value.copy(
                            notification = NotificationState.Snackbar(
                                message = "Не удалось загрузить вложение ${attachment.filename}: " +
                                    ErrorMapper.mapException(e).getUserMessage(),
                                duration = SnackbarDuration.Long
                            )
                        )
                    }
                },
                onSuccess = { uploaded ->
                    uploadedAttachment = uploaded.copy(localFilePath = localPath)
                }
            )

            if (uploadedAttachment == null) return null
            resolved += uploadedAttachment!!
        }

        if (resolved != attachments) {
            _uiState.value = _uiState.value.copy(attachments = resolved)
        }
        return resolved
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
