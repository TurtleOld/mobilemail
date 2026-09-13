package com.mobilemail.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateVersionCodeTest {

    @Test
    fun `encodes major minor patch using the documented formula`() {
        assertEquals(10203, UpdateVersionCode.encode(1, 2, 3))
        assertEquals(11200, UpdateVersionCode.encode(1, 12, 0))
    }

    @Test
    fun `rejects minor or patch outside 0 to 99`() {
        assertNull(UpdateVersionCode.encode(1, 100, 0))
        assertNull(UpdateVersionCode.encode(1, 0, 100))
        assertNull(UpdateVersionCode.encode(1, -1, 0))
    }

    @Test
    fun `rejects negative major`() {
        assertNull(UpdateVersionCode.encode(-1, 0, 0))
    }

    @Test
    fun `rejects codes outside the allowed android range`() {
        assertNull(UpdateVersionCode.encode(300000, 0, 0))
    }

    @Test
    fun `encodeFromTag parses a v-prefixed semver tag`() {
        assertEquals(10505, UpdateVersionCode.encodeFromTag("v1.5.5"))
    }

    @Test
    fun `encodeFromTag returns null for a malformed tag`() {
        assertNull(UpdateVersionCode.encodeFromTag("release-1.5.5"))
        assertNull(UpdateVersionCode.encodeFromTag("v1.5"))
    }
}
