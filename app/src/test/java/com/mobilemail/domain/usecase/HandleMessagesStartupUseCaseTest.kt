package com.mobilemail.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandleMessagesStartupUseCaseTest {
    private val useCase = HandleMessagesStartupUseCase()

    @Test
    fun `processes queue and subscribes topic before requesting notification permission`() = runTest {
        val calls = mutableListOf<String>()

        val action = useCase(
            accountId = "account-1",
            sdkInt = 33,
            tiramisuSdkInt = 33,
            alreadyRequestedPermission = false,
            processPending = { calls.add("process") },
            subscribeToTopic = { accountId -> calls.add("subscribe:$accountId") },
            markPermissionRequested = { calls.add("mark") }
        )

        assertEquals(
            HandleMessagesStartupUseCase.Action.RequestNotificationPermission,
            action
        )
        assertEquals(listOf("process", "subscribe:account-1", "mark"), calls)
    }

    @Test
    fun `does not mark permission when it was already requested`() = runTest {
        var marked = false

        val action = useCase(
            accountId = "account-1",
            sdkInt = 33,
            tiramisuSdkInt = 33,
            alreadyRequestedPermission = true,
            processPending = {},
            subscribeToTopic = {},
            markPermissionRequested = { marked = true }
        )

        assertEquals(
            HandleMessagesStartupUseCase.Action.NoPermissionRequest,
            action
        )
        assertFalse(marked)
    }

    @Test
    fun `marks permission on pre-tiramisu devices without requesting runtime permission`() = runTest {
        var marked = false

        val action = useCase(
            accountId = "account-1",
            sdkInt = 32,
            tiramisuSdkInt = 33,
            alreadyRequestedPermission = false,
            processPending = {},
            subscribeToTopic = {},
            markPermissionRequested = { marked = true }
        )

        assertEquals(
            HandleMessagesStartupUseCase.Action.NoPermissionRequest,
            action
        )
        assertTrue(marked)
    }
}
