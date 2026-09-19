package com.mobilemail.ui.settings

import app.cash.turbine.test
import com.mobilemail.data.update.GithubReleaseUpdateRepository
import com.mobilemail.domain.model.UpdateCheckResult
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val APPLICATION_ID = "app.turtleold.mobilemail"
private const val DEVICE_SDK = 34

/**
 * Проверяет поведение предложения обновления при запуске через публичную
 * границу [UpdateCheckCoordinator]: команды [UpdateCheckCoordinator.checkOnStartupOnce],
 * [UpdateCheckCoordinator.checkForUpdate], [UpdateCheckCoordinator.dismissOffer]
 * и наблюдаемые [UpdateCheckCoordinator.state] / [UpdateCheckCoordinator.isOfferDismissed].
 *
 * Граница теста — сам координатор, а не факт вызова [UpdateCheckCoordinator.checkOnStartupOnce]
 * в правильный момент: то, что автопроверка вызывается только после разблокировки PIN
 * (а без PIN — сразу при входе), обеспечивается вне координатора — эффектом
 * PinLockNavigationEffect в AppNavigationHost, который не даёт экрану со списком писем
 * (и его LaunchedEffect с автопроверкой) отрендериться, пока isPinLocked истинен.
 * Здесь проверяется, что сам координатор ведёт себя одинаково корректно независимо
 * от того, в какой момент к нему обратились.
 *
 * «Пересоздание Activity», «поворот» и «возврат из фона» моделируются повторным
 * обращением к тому же экземпляру координатора (он живёт в [UpdateCheckCoordinatorHolder]
 * на процесс, как и Activity его переживают). «Новый процесс» моделируется созданием
 * нового экземпляра координатора.
 */
class UpdateOfferCoordinatorBehaviorTest {

    private fun repository(server: MockWebServer) = GithubReleaseUpdateRepository(
        httpClient = OkHttpClient(),
        repoOwnerAndName = "turtleold/mobilemail",
        expectedApplicationId = APPLICATION_ID,
        deviceSdkInt = DEVICE_SDK,
        apiBaseUrl = server.url("/").toString().trimEnd('/')
    )

    private fun releasesBody(server: MockWebServer): String = """
        [
          {
            "tag_name": "v1.5.5",
            "draft": false,
            "prerelease": false,
            "assets": [
              {"name": "update-metadata.json", "size": 512, "browser_download_url": "${server.url("/assets/update-metadata.json")}"},
              {"name": "app-release.apk", "size": 12345678, "browser_download_url": "${server.url("/assets/app-release.apk")}"}
            ]
          }
        ]
    """.trimIndent()

    private fun metadataBody(): String = """
        {
          "schemaVersion": 1,
          "applicationId": "$APPLICATION_ID",
          "versionCode": 10505,
          "versionName": "1.5.5",
          "minSdk": 31,
          "apkAssetName": "app-release.apk",
          "apkSizeBytes": 12345678,
          "apkSha256": "${"a".repeat(64)}"
        }
    """.trimIndent()

    private fun enqueueUpdateAvailable(server: MockWebServer) {
        server.enqueue(MockResponse().setResponseCode(200).setBody(releasesBody(server)))
        server.enqueue(MockResponse().setResponseCode(200).setBody(metadataBody()))
    }

    @Test
    fun `startup check invoked as if after PIN unlock transitions idle to checking to update available`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            enqueueUpdateAvailable(server)
            val coordinator = UpdateCheckCoordinator(repository(server))

            coordinator.state.test {
                assertEquals(UpdateCheckResult.Idle, awaitItem())

                coordinator.checkOnStartupOnce(currentVersionCode = 10504)

                assertEquals(UpdateCheckResult.Checking, awaitItem())
                val updateAvailable = awaitItem()
                assertTrue(updateAvailable is UpdateCheckResult.UpdateAvailable)
                assertEquals("1.5.5", (updateAvailable as UpdateCheckResult.UpdateAvailable).versionName)
                assertEquals(12345678L, updateAvailable.apkSizeBytes)
            }
            assertEquals(2, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `startup check invoked as if PIN disabled runs immediately the same way`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            enqueueUpdateAvailable(server)
            val coordinator = UpdateCheckCoordinator(repository(server))

            coordinator.checkOnStartupOnce(currentVersionCode = 10504)

            assertTrue(coordinator.state.value is UpdateCheckResult.UpdateAvailable)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `dismissing the offer hides it until process end and survives activity recreation`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            enqueueUpdateAvailable(server)
            val coordinator = UpdateCheckCoordinator(repository(server))
            coordinator.checkOnStartupOnce(currentVersionCode = 10504)
            assertTrue(coordinator.state.value is UpdateCheckResult.UpdateAvailable)

            coordinator.dismissOffer()
            assertTrue(coordinator.isOfferDismissed.value)

            // Пересоздание Activity / поворот / возврат из фона: тот же экземпляр
            // координатора наблюдается заново, «Позже» остаётся в силе.
            assertTrue(coordinator.isOfferDismissed.value)
            assertTrue(coordinator.state.value is UpdateCheckResult.UpdateAvailable)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `new process gets a fresh coordinator where the offer is not dismissed`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            enqueueUpdateAvailable(server)
            val firstProcessCoordinator = UpdateCheckCoordinator(repository(server))
            firstProcessCoordinator.checkOnStartupOnce(currentVersionCode = 10504)
            firstProcessCoordinator.dismissOffer()
            assertTrue(firstProcessCoordinator.isOfferDismissed.value)

            // Новый процесс — новый экземпляр координатора (как из UpdateCheckCoordinatorHolder
            // после смерти процесса).
            val secondProcessCoordinator = UpdateCheckCoordinator(repository(server))

            assertFalse(secondProcessCoordinator.isOfferDismissed.value)
            assertEquals(UpdateCheckResult.Idle, secondProcessCoordinator.state.value)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `manual check from settings can show the offer again after later in the same process`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            enqueueUpdateAvailable(server)
            enqueueUpdateAvailable(server)
            val coordinator = UpdateCheckCoordinator(repository(server))

            coordinator.checkOnStartupOnce(currentVersionCode = 10504)
            coordinator.dismissOffer()
            assertTrue(coordinator.isOfferDismissed.value)

            coordinator.checkForUpdate(currentVersionCode = 10504)

            assertFalse(coordinator.isOfferDismissed.value)
            assertTrue(coordinator.state.value is UpdateCheckResult.UpdateAvailable)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `startup check runs only once per coordinator instance, a second call is a no-op`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            enqueueUpdateAvailable(server)
            val coordinator = UpdateCheckCoordinator(repository(server))

            coordinator.checkOnStartupOnce(currentVersionCode = 10504)
            assertEquals(2, server.requestCount)

            // Возврат из фона / повторная разблокировка / пересоздание Activity
            // вызывают checkOnStartupOnce заново на том же экземпляре — сети нет.
            coordinator.checkOnStartupOnce(currentVersionCode = 10504)
            coordinator.checkOnStartupOnce(currentVersionCode = 10504)

            assertEquals(2, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `a silent network error from the startup check does not surface as Failed`() = runTest {
        val server = MockWebServer()
        server.start()
        server.shutdown()
        val coordinator = UpdateCheckCoordinator(repository(server))

        coordinator.state.test {
            assertEquals(UpdateCheckResult.Idle, awaitItem())

            coordinator.checkOnStartupOnce(currentVersionCode = 10504)

            assertEquals(UpdateCheckResult.Checking, awaitItem())
            assertEquals(UpdateCheckResult.Idle, awaitItem())
        }
    }

    @Test
    fun `a manual check error after a silent startup failure is still visible to the user`() = runTest {
        val server = MockWebServer()
        server.start()
        server.shutdown()
        val coordinator = UpdateCheckCoordinator(repository(server))

        coordinator.checkOnStartupOnce(currentVersionCode = 10504)
        assertEquals(UpdateCheckResult.Idle, coordinator.state.value)

        coordinator.checkForUpdate(currentVersionCode = 10504)

        assertTrue(coordinator.state.value is UpdateCheckResult.Failed)
    }

    @Test
    fun `manual check that loses the race to an in-flight check keeps the offer dismissed`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            enqueueUpdateAvailable(server)
            val coordinator = UpdateCheckCoordinator(repository(server))
            coordinator.checkOnStartupOnce(currentVersionCode = 10504)
            coordinator.dismissOffer()
            assertTrue(coordinator.isOfferDismissed.value)

            val inFlight = launch(start = CoroutineStart.UNDISPATCHED) {
                coordinator.checkForUpdate(currentVersionCode = 10504)
            }
            // Проверка уже идёт (мьютекс занят), второй вызов должен ничего не делать.
            coordinator.checkForUpdate(currentVersionCode = 10504)
            inFlight.join()

            assertFalse(coordinator.isOfferDismissed.value)
        } finally {
            server.shutdown()
        }
    }
}
