package com.mobilemail.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class HandleMessagesStartupUseCaseTest {
    private val useCase = HandleMessagesStartupUseCase()

    @Test
    fun `requests permission on Android 13+ when not requested yet`() = runTest {
        val calls = mutableListOf<String>()

        val action = useCase(
            accountId = "acc-1",
            sdkInt = 33,
            tiramisuSdkInt = 33,
            alreadyRequestedPermission = false,
            processPending = { calls += "processPending" },
            subscribeToTopic = { calls += "subscribe:$it" },
            markPermissionRequested = { calls += "markRequested" }
        )

        assertEquals(
            listOf("processPending", "subscribe:acc-1", "markRequested"),
            calls
        )
        assertEquals(HandleMessagesStartupUseCase.Action.RequestNotificationPermission, action)
    }

    @Test
    fun `does not request permission when already requested`() = runTest {
        val calls = mutableListOf<String>()

        val action = useCase(
            accountId = "acc-1",
            sdkInt = 33,
            tiramisuSdkInt = 33,
            alreadyRequestedPermission = true,
            processPending = { calls += "processPending" },
            subscribeToTopic = { calls += "subscribe:$it" },
            markPermissionRequested = { calls += "markRequested" }
        )

        assertEquals(
            listOf("processPending", "subscribe:acc-1"),
            calls
        )
        assertEquals(HandleMessagesStartupUseCase.Action.NoPermissionRequest, action)
    }

    @Test
    fun `marks requested but does not request permission on pre-Android 13`() = runTest {
        val calls = mutableListOf<String>()

        val action = useCase(
            accountId = "acc-1",
            sdkInt = 32,
            tiramisuSdkInt = 33,
            alreadyRequestedPermission = false,
            processPending = { calls += "processPending" },
            subscribeToTopic = { calls += "subscribe:$it" },
            markPermissionRequested = { calls += "markRequested" }
        )

        assertEquals(
            listOf("processPending", "subscribe:acc-1", "markRequested"),
            calls
        )
        assertEquals(HandleMessagesStartupUseCase.Action.NoPermissionRequest, action)
    }
}
