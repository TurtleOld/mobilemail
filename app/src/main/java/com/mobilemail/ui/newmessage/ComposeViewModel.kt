package com.mobilemail.ui.newmessage

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mobilemail.data.common.fold
import com.mobilemail.data.jmap.JmapApi
import com.mobilemail.data.jmap.JmapClient
import com.mobilemail.data.jmap.JmapOAuthClient
import com.mobilemail.data.model.Attachment
import com.mobilemail.data.oauth.OAuthDiscovery
import com.mobilemail.data.oauth.TokenStore
import com.mobilemail.data.repository.ComposeRepository
import com.mobilemail.ui.common.ErrorMapper
import com.mobilemail.ui.common.NotificationState
import com.mobilemail.ui.common.SnackbarDuration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
    val notification: NotificationState = NotificationState.None
)

class ComposeViewModel(
    application: Application,
    private val server: String,
    private val email: String,
    private val password: String,
    private val accountId: String
) : AndroidViewModel(application) {
    companion object {
        private const val MAX_ATTACHMENT_SIZE_BYTES = 10 * 1024 * 1024L
    }
    private val _uiState = MutableStateFlow(ComposeUiState())
    val uiState: StateFlow<ComposeUiState> = _uiState

    private val jmapClient: JmapApi = buildJmapClient()
    private val repository = ComposeRepository(jmapClient)

    private fun buildJmapClient(): JmapApi {
        if (password.isBlank() || password == "-") {
            val tokenStore = TokenStore(getApplication())
            val tokens = tokenStore.getTokens(server, email)
            Log.d(
                "ComposeViewModel",
                "Проверка OAuth токенов: found=${tokens != null}, accessValid=${tokens?.isValid()}, hasRefresh=${tokens?.refreshToken != null}"
            )
            if (password == "-") {
                Log.d("ComposeViewModel", "Обнаружен OAuth placeholder пароля ('-'), используем OAuth клиент")
            }
            if (tokens != null) {
                return runBlocking {
                    try {
                        val httpClient = OAuthDiscovery.createClient()
                        val discovery = OAuthDiscovery(httpClient)
                        val discoveryUrl = "$server/.well-known/oauth-authorization-server"
                        val metadata = discovery.discover(discoveryUrl)
                        Log.d("ComposeViewModel", "Создаем JmapOAuthClient")
                        JmapOAuthClient.getOrCreate(
                            serverUrl = server,
                            email = email,
                            accountId = accountId,
                            tokenStore = tokenStore,
                            metadata = metadata,
                            clientId = "mail-client"
                        )
                    } catch (e: Exception) {
                        Log.e("ComposeViewModel", "Ошибка создания OAuth клиента, fallback на basic", e)
                        JmapClient.getOrCreate(server, email, "", accountId)
                    }
                }
            } else {
                Log.w("ComposeViewModel", "OAuth токены не найдены, используем basic")
                return JmapClient.getOrCreate(server, email, password, accountId)
            }
        }
        return JmapClient.getOrCreate(server, email, password, accountId)
    }

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
            "Отправка письма: toCount=${recipients.size}, subjectLength=${trimmedSubject.length}, bodyLength=${trimmedBody.length}, attachments=${_uiState.value.attachments.size}, draftId=${_uiState.value.draftId}"
        )

        if (recipients.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                notification = NotificationState.Snackbar(
                    message = "Введите адрес получателя",
                    duration = com.mobilemail.ui.common.SnackbarDuration.Short
                )
            )
            return
        }

        if (trimmedSubject.isBlank() && trimmedBody.isBlank()) {
            _uiState.value = _uiState.value.copy(
                notification = NotificationState.Snackbar(
                    message = "Письмо пустое",
                    duration = com.mobilemail.ui.common.SnackbarDuration.Short
                )
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true)
            repository.sendMessage(
                from = email,
                to = recipients,
                subject = trimmedSubject,
                body = trimmedBody,
                attachments = _uiState.value.attachments,
                draftId = _uiState.value.draftId
            ).fold(
                onError = { e ->
                    Log.e("ComposeViewModel", "Ошибка отправки письма", e)
                    _uiState.value = _uiState.value.copy(
                        isSending = false,
                        notification = NotificationState.Snackbar(
                            message = "Не удалось отправить письмо: ${ErrorMapper.mapException(e).getUserMessage()}",
                            duration = SnackbarDuration.Long
                        )
                    )
                },
                onSuccess = { submissionId ->
                    Log.d("ComposeViewModel", "EmailSubmission создан, submissionId=$submissionId")

                    // Письмо принято сервером на отправку. Дальше делаем polling статуса,
                    // чтобы показать пользователю, если доставка провалилась.
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

                    viewModelScope.launch {
                        pollSubmissionStatus(submissionId)
                    }
                }
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
                    // Сервер может не поддерживать EmailSubmission/get — тогда не тревожим пользователя.
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
                    // Неформальный best-effort: если есть текст SMTP reply/ошибки — покажем.
                    val text = status.lastStatusText
                    if (!text.isNullOrBlank()) {
                        _uiState.value = _uiState.value.copy(
                            notification = NotificationState.Snackbar(
                                message = "Статус доставки: $text",
                                duration = SnackbarDuration.Long
                            )
                        )
                        return
                    }

                    // Если сервер даёт delivered/failed — можно завершить.
                    if (status.failed == true) {
                        _uiState.value = _uiState.value.copy(
                            notification = NotificationState.Snackbar(
                                message = "Доставка письма не удалась",
                                duration = SnackbarDuration.Long
                            )
                        )
                        return
                    }
                    if (status.delivered == true) {
                        _uiState.value = _uiState.value.copy(
                            notification = NotificationState.Snackbar(
                                message = "Письмо доставлено",
                                duration = SnackbarDuration.Short
                            )
                        )
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
                runCatching {
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
                        throw IllegalArgumentException("Размер вложения больше 10 МБ")
                    }

                    val type = resolver.getType(uri) ?: "application/octet-stream"
                    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalArgumentException("Не удалось прочитать файл")
                    if (bytes.size > MAX_ATTACHMENT_SIZE_BYTES) {
                        throw IllegalArgumentException("Размер вложения больше 10 МБ")
                    }
                    Triple(name, type, bytes)
                }
            }

            result.fold(
                onSuccess = { (name, type, bytes) ->
                    _uiState.value = _uiState.value.copy(
                        notification = NotificationState.Snackbar(
                            message = "Загрузка вложения: $name",
                            duration = com.mobilemail.ui.common.SnackbarDuration.Short
                        )
                    )
                    repository.uploadAttachment(bytes, type, name).fold(
                        onError = { e ->
                            Log.e("ComposeViewModel", "Ошибка загрузки вложения", e)
                            _uiState.value = _uiState.value.copy(
                                notification = NotificationState.Snackbar(
                                    message = "Не удалось загрузить вложение: ${ErrorMapper.mapException(e).getUserMessage()}",
                                    duration = com.mobilemail.ui.common.SnackbarDuration.Long
                                )
                            )
                        },
                        onSuccess = { attachment ->
                            _uiState.value = _uiState.value.copy(
                                attachments = _uiState.value.attachments + attachment,
                                notification = NotificationState.Snackbar(
                                    message = "Вложение добавлено: ${attachment.filename}",
                                    duration = com.mobilemail.ui.common.SnackbarDuration.Short
                                )
                            )
                        }
                    )
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

    fun removeAttachment(attachment: Attachment) {
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
            "Сохранение черновика: toCount=${to.count { it.isNotBlank() }}, subjectLength=${trimmedSubject.length}, bodyLength=${trimmedBody.length}, attachments=${_uiState.value.attachments.size}, draftId=${_uiState.value.draftId}"
        )

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSavingDraft = true)
            repository.saveDraft(
                from = email,
                to = to.map { it.trim() }.filter { it.isNotBlank() },
                subject = trimmedSubject,
                body = trimmedBody,
                attachments = _uiState.value.attachments,
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

    fun clearNotification() {
        _uiState.value = _uiState.value.copy(notification = NotificationState.None)
    }
}