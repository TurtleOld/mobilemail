package com.mobilemail.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Проверяет [isApkExpired] через управляемое время: границу ровно в 7 суток и
 * отсутствие срока, пока загрузка не завершалась.
 */
class UpdateApkExpiryTest {

    @Test
    fun `apk is usable up to but not at the seven day boundary`() {
        val completedAt = 1_000L
        assertFalse(isApkExpired(completedAt, completedAt))
        assertFalse(isApkExpired(completedAt, completedAt + APK_EXPIRY_MILLIS - 1))
        assertTrue(isApkExpired(completedAt, completedAt + APK_EXPIRY_MILLIS))
        assertTrue(isApkExpired(completedAt, completedAt + APK_EXPIRY_MILLIS + 1))
    }

    @Test
    fun `unknown completion time is never treated as expired`() {
        assertFalse(isApkExpired(null, 0L))
        assertFalse(isApkExpired(null, Long.MAX_VALUE))
    }
}
