package com.mobilemail.ui.newmessage

import com.mobilemail.domain.common.Result
import com.mobilemail.domain.model.Attachment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Снимок редактируемого письма, который передаётся в сохранение или отправку.
 */
data class DraftSnapshot(
    val to: List<String> = emptyList(),
    val subject: String = "",
    val body: String = "",
    val attachments: List<Attachment> = emptyList()
) {
    val isEmpty: Boolean
        get() = to.isEmpty() && subject.isBlank() && body.isBlank() && attachments.isEmpty()
}

/**
 * Последовательно выполняет сохранения и отправку одного черновика.
 *
 * В любой момент выполняется не больше одной операции над черновиком: отправка ждёт
 * начатое сохранение, а правки, пришедшие во время сохранения, склеиваются в один запрос
 * с последним снимком. Идентификатор черновика меняет только завершившееся сохранение,
 * поэтому устаревший ответ не может заменить актуальный.
 */
class DraftSyncCoordinator(
    private val scope: CoroutineScope,
    private val saveDraft: suspend (snapshot: DraftSnapshot, draftId: String?) -> Result<String>,
    private val sendMessage: suspend (snapshot: DraftSnapshot, draftId: String?) -> Result<String>
) {

    private val mutex = Mutex()
    private val pending = MutableStateFlow<DraftSnapshot?>(null)
    private val _isSaving = MutableStateFlow(false)

    @Volatile
    var draftId: String? = null
        private set

    /** Истина, пока есть сохранение в работе или в очереди. */
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private var persisted: DraftSnapshot? = null
    private var sent = false

    /** Ставит снимок в очередь на сохранение, вытесняя ещё не начатые правки. */
    fun requestSave(snapshot: DraftSnapshot) {
        if (sent) return
        pending.value = snapshot
        _isSaving.value = true
        scope.launch { drain() }
    }

    /** Дожидается текущего сохранения и отправляет последний снимок с актуальным draftId. */
    suspend fun send(snapshot: DraftSnapshot): Result<String> = mutex.withLock {
        pending.value = null
        val result = sendMessage(snapshot, draftId)
        if (result.isSuccess) {
            sent = true
            draftId = null
            persisted = null
        } else {
            // Письмо не ушло, поэтому несохранённые правки возвращаются в очередь.
            pending.value = snapshot
            scope.launch { drain() }
        }
        _isSaving.value = pending.value != null
        result
    }

    private suspend fun drain() {
        while (true) {
            val snapshot = takePending() ?: break
            mutex.withLock {
                when {
                    sent -> pending.value = null
                    snapshot != persisted -> persist(snapshot)
                }
            }
            _isSaving.value = pending.value != null
        }
        _isSaving.value = pending.value != null
    }

    private fun takePending(): DraftSnapshot? {
        while (true) {
            val current = pending.value ?: return null
            if (pending.compareAndSet(current, null)) return current
        }
    }

    private suspend fun persist(snapshot: DraftSnapshot) {
        _isSaving.value = true
        saveDraft(snapshot, draftId).onSuccess { newDraftId ->
            draftId = newDraftId
            persisted = snapshot
        }
    }
}
