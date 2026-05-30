package com.mobilemail.domain.usecase

import com.mobilemail.data.preferences.SavedSession
import com.mobilemail.notifications.PushMessageTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResolvePushSessionUseCaseTest {
    private val useCase = ResolvePushSessionUseCase()

    @Test
    fun `returns current session when it matches target`() {
        val current = SavedSession("https://mail.example.com", "user@example.com", "acc-1")
        val other = SavedSession("https://mail.example.com", "other@example.com", "acc-2")
        val target = PushMessageTarget(messageId = "m-1", email = "user@example.com")

        val resolved = useCase(target, current, listOf(other))

        assertEquals(current, resolved)
    }

    @Test
    fun `returns matching saved account when current does not match`() {
        val current = SavedSession("https://mail.example.com", "user@example.com", "acc-1")
        val matching = SavedSession("https://mail.example.com", "other@example.com", "acc-2")
        val target = PushMessageTarget(messageId = "m-1", email = "other@example.com")

        val resolved = useCase(target, current, listOf(matching))

        assertEquals(matching, resolved)
    }

    @Test
    fun `falls back to current when no accounts match`() {
        val current = SavedSession("https://mail.example.com", "user@example.com", "acc-1")
        val target = PushMessageTarget(messageId = "m-1", email = "nomatch@example.com")

        val resolved = useCase(target, current, emptyList())

        assertEquals(current, resolved)
    }

    @Test
    fun `returns last saved account when current is null and no match`() {
        val first = SavedSession("https://mail.example.com", "one@example.com", "acc-1")
        val last = SavedSession("https://mail.example.com", "two@example.com", "acc-2")
        val target = PushMessageTarget(messageId = "m-1", email = "nomatch@example.com")

        val resolved = useCase(target, null, listOf(first, last))

        assertEquals(last, resolved)
    }

    @Test
    fun `returns null when no sessions available`() {
        val target = PushMessageTarget(messageId = "m-1")

        val resolved = useCase(target, null, emptyList())

        assertNull(resolved)
    }
}
