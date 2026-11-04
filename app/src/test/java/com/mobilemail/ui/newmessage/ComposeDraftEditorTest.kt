@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mobilemail.ui.newmessage

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Проверяет [ComposeDraftEditor] через его публичную границу: текст [ComposeDraftEditor.draft],
 * [ComposeDraftEditor.autosave] и [ComposeDraftEditor.flush]. Сохранение на сервер подменяется
 * лямбдой, которая решает, подтверждена ли запись.
 */
class ComposeDraftEditorTest {

    @Test
    fun `draft survives screen recreation`() {
        val editor = ComposeDraftEditor(saveDraft = { true })
        editor.seed(to = "a@example.com", subject = "Тема", body = "Текст")
        editor.updateBody("Текст с правкой")

        // Пересоздание экрана: новая композиция заново подставляет переданный текст.
        editor.seed(to = "a@example.com", subject = "Тема", body = "Текст")

        assertEquals("a@example.com", editor.draft.value.to)
        assertEquals("Тема", editor.draft.value.subject)
        assertEquals("Текст с правкой", editor.draft.value.body)
    }

    @Test
    fun `exit before autosave delay saves the latest edit`() = runTest {
        val saved = mutableListOf<ComposeDraft>()
        val editor = ComposeDraftEditor(saveDraft = { draft ->
            saved += draft
            true
        })
        editor.updateTo("a@example.com")
        editor.updateSubject("Тема")
        editor.updateBody("Последняя правка")

        assertTrue(editor.flush())

        assertEquals(1, saved.size)
        assertEquals("Последняя правка", saved.single().body)
        assertFalse(editor.isDirty())
    }

    @Test
    fun `failed save is not marked as saved and can be retried`() = runTest {
        var serverAccepts = false
        val saved = mutableListOf<ComposeDraft>()
        val editor = ComposeDraftEditor(saveDraft = { draft ->
            saved += draft
            serverAccepts
        })
        editor.updateSubject("Тема")
        editor.updateBody("Текст")

        assertFalse(editor.flush())
        assertTrue(editor.isDirty())

        serverAccepts = true
        assertTrue(editor.flush())

        assertFalse(editor.isDirty())
        assertEquals(listOf("Текст", "Текст"), saved.map { it.body })
    }

    @Test
    fun `autosave saves after the debounce and skips an unchanged draft`() = runTest {
        var calls = 0
        val editor = ComposeDraftEditor(saveDraft = {
            calls++
            true
        })
        editor.updateBody("Черновик")

        val job = launch { editor.autosave() }
        advanceTimeBy(ComposeDraftEditor.AUTOSAVE_DEBOUNCE_MILLIS - 1)
        runCurrent()
        assertEquals(0, calls)

        advanceTimeBy(2)
        runCurrent()
        assertEquals(1, calls)
        job.join()

        assertTrue(editor.autosave())
        assertEquals(1, calls)
    }

    @Test
    fun `autosave skips an empty draft`() = runTest {
        var calls = 0
        val editor = ComposeDraftEditor(saveDraft = {
            calls++
            true
        })

        assertTrue(editor.autosave())
        assertTrue(editor.flush())
        assertEquals(0, calls)
    }

    @Test
    fun `exit during in-flight autosave still saves the latest text`() = runTest {
        val saved = mutableListOf<String>()
        val gate = CompletableDeferred<Unit>()
        val editor = ComposeDraftEditor(saveDraft = { draft ->
            saved += draft.body
            gate.await()
            true
        })
        editor.updateBody("Черновик")

        val autosave = async { editor.autosave() }
        advanceTimeBy(ComposeDraftEditor.AUTOSAVE_DEBOUNCE_MILLIS)
        runCurrent()

        editor.updateBody("Черновик с правкой")
        val flush = async { editor.flush() }
        runCurrent()
        gate.complete(Unit)

        assertTrue(autosave.await())
        assertTrue(flush.await())
        assertEquals(listOf("Черновик", "Черновик с правкой"), saved)
    }

    @Test
    fun `cancelled autosave still records the confirmed save`() = runTest {
        val saved = mutableListOf<String>()
        val gate = CompletableDeferred<Unit>()
        val editor = ComposeDraftEditor(saveDraft = { draft ->
            saved += draft.body
            gate.await()
            true
        })
        editor.updateBody("Текст")

        val autosave = launch { editor.autosave() }
        advanceTimeBy(ComposeDraftEditor.AUTOSAVE_DEBOUNCE_MILLIS)
        runCurrent()

        // Экран пересоздан, пока запрос в пути: запись завершается и не повторяется.
        autosave.cancel()
        gate.complete(Unit)
        runCurrent()

        assertFalse(editor.isDirty())
        assertTrue(editor.flush())
        assertEquals(listOf("Текст"), saved)
    }
}
