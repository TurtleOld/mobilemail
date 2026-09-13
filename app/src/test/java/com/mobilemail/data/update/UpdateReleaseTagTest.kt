package com.mobilemail.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateReleaseTagTest {

    @Test
    fun `parses a v-prefixed tag`() {
        assertEquals(Triple(1, 5, 5), UpdateReleaseTag.parse("v1.5.5"))
    }

    @Test
    fun `parses a tag without the v prefix`() {
        assertEquals(Triple(2, 0, 10), UpdateReleaseTag.parse("2.0.10"))
    }

    @Test
    fun `rejects malformed tags`() {
        assertNull(UpdateReleaseTag.parse("v1.5"))
        assertNull(UpdateReleaseTag.parse("release-1.5.5"))
        assertNull(UpdateReleaseTag.parse(""))
    }
}
