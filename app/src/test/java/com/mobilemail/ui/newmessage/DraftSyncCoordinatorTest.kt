@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mobilemail.ui.newmessage

import com.mobilemail.domain.common.Result
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

private const val SAVE_DELAY_MS = 1_000L
private const val SEND_DELAY_MS = 2_000L
private const val STEP_MS = 10_000L

/**
 * Проверяет согласованность автосохранений и отправки через публичную границу
 * [DraftSyncCoordinator]: [DraftSyncCoordinator.requestSave], [DraftSyncCoordinator.send],
 * [DraftSyncCoordinator.draftId] и [DraftSyncCoordinator.isSaving]. Сетевые вызовы
 * заменены fake-лямбдами с управляемыми задержками.
 */
class DraftSyncCoordinatorTest {

    private val unexpectedSend: suspend (DraftSnapshot, String?) -> Result<String> = { _, _ ->
        Result.Error(IllegalStateException("отправка не ожидалась"))
    }

    @Test
    fun `editor flush waits for confirmation and send uses the saved draft id`() = runTest {
        val editorSnapshot = DraftSnapshot(subject = "editor")
        var sentDraftId: String? = null
        val coordinator = DraftSyncCoordinator(
            scope = backgroundScope,
            saveDraft = { _, _ -> delay(SAVE_DELAY_MS); Result.Success("editor-id") },
            sendMessage = { _, id -> sentDraftId = id; Result.Success("submission") }
        )
        val saved = async { coordinator.save(editorSnapshot) }
        runCurrent()
        assertFalse(saved.isCompleted)
        val sent = async { coordinator.send(editorSnapshot) }
        runCurrent()
        assertFalse(sent.isCompleted)
        advanceTimeBy(STEP_MS)
        runCurrent()
        assertTrue(saved.await())
        assertTrue(sent.await().isSuccess)
        assertEquals("editor-id", sentDraftId)
        assertFalse(coordinator.isSaving.value)
    }

    @Test
    fun `second autosave waits for the first and persists the newest snapshot`() = runTest {
        val saves = mutableListOf<RecordedCall>()
        var active = 0
        var maxActive = 0
        val coordinator = DraftSyncCoordinator(
            scope = backgroundScope,
            saveDraft = { snapshot, draftId ->
                active++
                maxActive = maxOf(maxActive, active)
                delay(SAVE_DELAY_MS)
                saves += RecordedCall(snapshot.subject, draftId)
                active--
                Result.Success("draft-${saves.size}")
            },
            sendMessage = unexpectedSend
        )

        coordinator.requestSave(DraftSnapshot(subject = "first"))
        runCurrent()
        coordinator.requestSave(DraftSnapshot(subject = "second"))
        runCurrent()

        assertEquals("второе сохранение не стартует параллельно", 0, saves.size)
        assertEquals(1, active)
        assertTrue(coordinator.isSaving.value)

        advanceTimeBy(STEP_MS)
        runCurrent()

        assertEquals(
            listOf(RecordedCall("first", null), RecordedCall("second", "draft-1")),
            saves
        )
        assertEquals("сохранения не выполнялись параллельно", 1, maxActive)
        assertEquals("draft-2", coordinator.draftId)
        assertFalse(coordinator.isSaving.value)
    }

    @Test
    fun `edits made during a save are coalesced into the newest snapshot`() = runTest {
        val saves = mutableListOf<RecordedCall>()
        val coordinator = DraftSyncCoordinator(
            scope = backgroundScope,
            saveDraft = { snapshot, draftId ->
                delay(SAVE_DELAY_MS)
                saves += RecordedCall(snapshot.subject, draftId)
                Result.Success("draft-${saves.size}")
            },
            sendMessage = unexpectedSend
        )

        coordinator.requestSave(DraftSnapshot(subject = "one"))
        runCurrent()
        coordinator.requestSave(DraftSnapshot(subject = "two"))
        coordinator.requestSave(DraftSnapshot(subject = "three"))
        advanceTimeBy(STEP_MS)
        runCurrent()

        assertEquals(
            "промежуточные правки склеиваются в последний снимок",
            listOf(RecordedCall("one", null), RecordedCall("three", "draft-1")),
            saves
        )
    }

    @Test
    fun `failed save keeps the current draft id for the next save`() = runTest {
        val draftIds = mutableListOf<String?>()
        var attempt = 0
        val coordinator = DraftSyncCoordinator(
            scope = backgroundScope,
            saveDraft = { _, draftId ->
                draftIds += draftId
                attempt++
                if (attempt == 2) Result.Error(IOException("нет сети")) else Result.Success("draft-$attempt")
            },
            sendMessage = unexpectedSend
        )

        coordinator.requestSave(DraftSnapshot(subject = "one"))
        runCurrent()
        advanceTimeBy(STEP_MS)
        runCurrent()
        coordinator.requestSave(DraftSnapshot(subject = "two"))
        runCurrent()
        advanceTimeBy(STEP_MS)
        runCurrent()
        coordinator.requestSave(DraftSnapshot(subject = "three"))
        runCurrent()
        advanceTimeBy(STEP_MS)
        runCurrent()

        assertEquals(listOf(null, "draft-1", "draft-1"), draftIds)
        assertEquals("draft-3", coordinator.draftId)
    }

    @Test
    fun `send during a save waits for it and sends the latest snapshot`() = runTest {
        val events = mutableListOf<String>()
        val saves = mutableListOf<RecordedCall>()
        val sends = mutableListOf<RecordedCall>()
        var active = 0
        var maxActive = 0
        val coordinator = DraftSyncCoordinator(
            scope = backgroundScope,
            saveDraft = { snapshot, draftId ->
                active++
                maxActive = maxOf(maxActive, active)
                delay(SAVE_DELAY_MS)
                active--
                saves += RecordedCall(snapshot.subject, draftId)
                events += "save:${snapshot.subject}"
                Result.Success("draft-${saves.size}")
            },
            sendMessage = { snapshot, draftId ->
                active++
                maxActive = maxOf(maxActive, active)
                delay(SEND_DELAY_MS)
                active--
                sends += RecordedCall(snapshot.subject, draftId)
                events += "send:${snapshot.subject}"
                Result.Success("submission-1")
            }
        )

        coordinator.requestSave(DraftSnapshot(subject = "old"))
        runCurrent()
        val sendResult = async { coordinator.send(DraftSnapshot(subject = "new")) }
        runCurrent()

        assertTrue("отправка не стартует во время сохранения", sends.isEmpty())
        assertEquals(1, active)

        advanceTimeBy(STEP_MS)
        runCurrent()

        assertEquals(Result.Success("submission-1"), sendResult.await())
        assertEquals(listOf("save:old", "send:new"), events)
        assertEquals(listOf<RecordedCall>(RecordedCall("old", null)), saves)
        assertEquals("draft-1", sends.single().draftId)
        assertEquals("new", sends.single().subject)
        assertEquals("операции не выполнялись параллельно", 1, maxActive)
        assertNull(coordinator.draftId)
        assertFalse(coordinator.isSaving.value)
    }

    @Test
    fun `autosave requested during send does not recreate the sent draft`() = runTest {
        val saveSubjects = mutableListOf<String>()
        val coordinator = DraftSyncCoordinator(
            scope = backgroundScope,
            saveDraft = { snapshot, _ ->
                saveSubjects += snapshot.subject
                Result.Success("draft-${saveSubjects.size}")
            },
            sendMessage = { _, _ ->
                delay(SEND_DELAY_MS)
                Result.Success("submission-1")
            }
        )

        val sendResult = async { coordinator.send(DraftSnapshot(subject = "sent")) }
        runCurrent()
        coordinator.requestSave(DraftSnapshot(subject = "late"))
        advanceTimeBy(STEP_MS)
        runCurrent()

        assertEquals(Result.Success("submission-1"), sendResult.await())
        assertEquals("отложенное автосохранение не выполняется", emptyList<String>(), saveSubjects)
        assertNull("отправленное письмо не оставляет новый черновик", coordinator.draftId)
        assertFalse(coordinator.isSaving.value)

        coordinator.requestSave(DraftSnapshot(subject = "after send"))
        advanceTimeBy(STEP_MS)
        runCurrent()

        assertEquals("после отправки автосохранение игнорируется", emptyList<String>(), saveSubjects)
    }

    @Test
    fun `failed send keeps the draft and saves the latest snapshot`() = runTest {
        val saveSubjects = mutableListOf<String>()
        val coordinator = DraftSyncCoordinator(
            scope = backgroundScope,
            saveDraft = { snapshot, _ ->
                delay(SAVE_DELAY_MS)
                saveSubjects += snapshot.subject
                Result.Success("draft-${saveSubjects.size}")
            },
            sendMessage = { _, _ -> Result.Error(IOException("сервер недоступен")) }
        )

        coordinator.requestSave(DraftSnapshot(subject = "old"))
        runCurrent()
        val sendResult = async { coordinator.send(DraftSnapshot(subject = "new")) }
        runCurrent()
        advanceTimeBy(STEP_MS)
        runCurrent()

        assertTrue(sendResult.await().isError)
        assertEquals("последний снимок сохранён после неудачной отправки", listOf("old", "new"), saveSubjects)
        assertEquals("Черновик остаётся доступным для повтора", "draft-2", coordinator.draftId)
        assertFalse(coordinator.isSaving.value)
    }

    @Test
    fun `send reuses the current draft id without saving again`() = runTest {
        val saves = mutableListOf<RecordedCall>()
        val coordinator = DraftSyncCoordinator(
            scope = backgroundScope,
            saveDraft = { snapshot, draftId ->
                saves += RecordedCall(snapshot.subject, draftId)
                Result.Success("draft-1")
            },
            sendMessage = { _, draftId -> Result.Success("submission-$draftId") }
        )

        coordinator.requestSave(DraftSnapshot(subject = "current"))
        runCurrent()
        advanceTimeBy(STEP_MS)
        runCurrent()
        val result = coordinator.send(DraftSnapshot(subject = "current"))

        assertEquals(Result.Success("submission-draft-1"), result)
        assertEquals("снимок уже сохранён, повторное сохранение не нужно", 1, saves.size)
    }

    private data class RecordedCall(val subject: String, val draftId: String?)
}
