package com.mobilemail.ui.newmessage

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Текст черновика, который вводит пользователь. */
data class ComposeDraft(
    val to: String = "",
    val subject: String = "",
    val body: String = ""
) {
    val isEmpty: Boolean
        get() = to.isBlank() && subject.isBlank() && body.isBlank()
}

/**
 * Хранит текст черновика и сохраняет его на сервере с задержкой [debounceMillis].
 *
 * Состояние живёт вне композиции, поэтому переживает пересоздание экрана.
 * Снимок считается сохранённым только после того, как [saveDraft] вернул `true`.
 *
 * @param saveDraft сохраняет черновик; возвращает `true`, только когда сервер подтвердил запись.
 */
class ComposeDraftEditor(
    private val saveDraft: suspend (ComposeDraft) -> Boolean,
    private val debounceMillis: Long = AUTOSAVE_DEBOUNCE_MILLIS
) {
    private val _draft = MutableStateFlow(ComposeDraft())
    val draft: StateFlow<ComposeDraft> = _draft.asStateFlow()

    // Сериализует записи: иначе автосохранение и выход могут создать два черновика.
    private val saveMutex = Mutex()
    private var savedDraft: ComposeDraft? = null
    private var seeded = false

    /** Подставляет переданный текст (ответ, пересылку) один раз — при повторном создании экрана не затирает правки. */
    fun seed(to: String, subject: String, body: String) {
        if (seeded) return
        seeded = true
        if (to.isBlank() && subject.isBlank() && body.isBlank()) return
        _draft.value = ComposeDraft(to = to, subject = subject, body = body)
    }

    fun updateTo(value: String) {
        _draft.value = _draft.value.copy(to = value)
    }

    fun updateSubject(value: String) {
        _draft.value = _draft.value.copy(subject = value)
    }

    fun updateBody(value: String) {
        _draft.value = _draft.value.copy(body = value)
    }

    fun appendSignatureIfEmpty(signature: String) {
        if (signature.isBlank() || _draft.value.body.isNotBlank()) return
        _draft.value = _draft.value.copy(body = "\n\n$signature")
    }

    fun isDirty(): Boolean = !_draft.value.isEmpty && _draft.value != savedDraft

    /** Ждёт [debounceMillis] и сохраняет изменившийся черновик; возвращает `true`, если сохранять нечего или запись подтверждена. */
    suspend fun autosave(): Boolean {
        if (!isDirty()) return true
        delay(debounceMillis)
        return saveIfChanged()
    }

    /** Сохраняет черновик сразу, не дожидаясь задержки автосохранения. */
    suspend fun flush(): Boolean = saveIfChanged()

    private suspend fun saveIfChanged(): Boolean = saveMutex.withLock {
        val snapshot = _draft.value
        if (snapshot.isEmpty || snapshot == savedDraft) return@withLock true
        // Запись не отменяем: отмена после отправки запроса оставила бы на сервере лишний черновик.
        val saved = withContext(NonCancellable) { saveDraft(snapshot) }
        if (saved) savedDraft = snapshot
        saved
    }

    companion object {
        const val AUTOSAVE_DEBOUNCE_MILLIS = 1200L
    }
}
