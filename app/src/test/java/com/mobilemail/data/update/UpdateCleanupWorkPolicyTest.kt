package com.mobilemail.data.update

import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Проверяет расчёт задержки фоновой очистки и отсутствие сетевого ограничения:
 * удаление локального файла не должно ждать подключения.
 */
class UpdateCleanupWorkPolicyTest {

    @Test
    fun `cleanup delay counts down to the expiry deadline`() {
        val completedAt = 5_000L
        assertEquals(APK_EXPIRY_MILLIS, updateCleanupDelayMillis(completedAt, completedAt))
        assertEquals(60_000L, updateCleanupDelayMillis(completedAt, completedAt + APK_EXPIRY_MILLIS - 60_000L))
    }

    @Test
    fun `cleanup delay is zero at and after the deadline`() {
        val completedAt = 5_000L
        assertEquals(0L, updateCleanupDelayMillis(completedAt, completedAt + APK_EXPIRY_MILLIS))
        assertEquals(0L, updateCleanupDelayMillis(completedAt, completedAt + APK_EXPIRY_MILLIS + 60_000L))
    }

    @Test
    fun `cleanup work is not restricted to a network type`() {
        val request = UpdateCleanupWorkPolicy.buildWorkRequest(60_000L)
        assertEquals(NetworkType.NOT_REQUIRED, request.workSpec.constraints.requiredNetworkType)
    }
}
