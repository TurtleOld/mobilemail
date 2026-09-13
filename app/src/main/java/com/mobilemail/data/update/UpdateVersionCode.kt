package com.mobilemail.data.update

/**
 * Формула versionCode = major * 10000 + minor * 100 + patch.
 *
 * minor/patch ограничены диапазоном 0..99, чтобы избежать коллизий кодов
 * между соседними major-версиями; итоговый код должен укладываться
 * в допустимый для Android диапазон.
 */
object UpdateVersionCode {
    private const val MINOR_PATCH_MAX = 99
    private const val MULTIPLIER_MAJOR = 10_000
    private const val MULTIPLIER_MINOR = 100
    private const val MAX_ANDROID_VERSION_CODE = 2_100_000_000

    fun encode(major: Int, minor: Int, patch: Int): Int? {
        if (major < 0 || minor !in 0..MINOR_PATCH_MAX || patch !in 0..MINOR_PATCH_MAX) return null
        val code = major * MULTIPLIER_MAJOR + minor * MULTIPLIER_MINOR + patch
        return code.takeIf { it in 1..MAX_ANDROID_VERSION_CODE }
    }

    fun encodeFromTag(tag: String): Int? {
        val (major, minor, patch) = UpdateReleaseTag.parse(tag) ?: return null
        return encode(major, minor, patch)
    }
}
