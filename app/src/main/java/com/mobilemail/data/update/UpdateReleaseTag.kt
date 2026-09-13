package com.mobilemail.data.update

/**
 * Разбирает теги релизов вида `v1.2.3` (или `1.2.3`) на компоненты semver.
 */
object UpdateReleaseTag {
    private val TAG_PATTERN = Regex("""^v?(\d+)\.(\d+)\.(\d+)$""")

    fun parse(tag: String): Triple<Int, Int, Int>? {
        val match = TAG_PATTERN.matchEntire(tag.trim()) ?: return null
        val (major, minor, patch) = match.destructured
        return Triple(major.toInt(), minor.toInt(), patch.toInt())
    }
}
