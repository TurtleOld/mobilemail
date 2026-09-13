package com.mobilemail.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UpdateMetadataTest {

    private val validJson = """
        {
          "schemaVersion": 1,
          "applicationId": "app.turtleold.mobilemail",
          "versionCode": 10505,
          "versionName": "1.5.5",
          "minSdk": 31,
          "apkAssetName": "app-release.apk",
          "apkSizeBytes": 12345678,
          "apkSha256": "${"a".repeat(64)}"
        }
    """.trimIndent()

    @Test
    fun `parses valid metadata`() {
        val metadata = UpdateMetadata.parse(validJson)

        assertEquals(1, metadata.schemaVersion)
        assertEquals("app.turtleold.mobilemail", metadata.applicationId)
        assertEquals(10505, metadata.versionCode)
        assertEquals("1.5.5", metadata.versionName)
        assertEquals(31, metadata.minSdk)
        assertEquals("app-release.apk", metadata.apkAssetName)
        assertEquals(12345678L, metadata.apkSizeBytes)
        assertEquals("a".repeat(64), metadata.apkSha256)
    }

    @Test
    fun `rejects malformed json`() {
        assertThrows(UpdateMetadataException::class.java) {
            UpdateMetadata.parse("not json")
        }
    }

    @Test
    fun `rejects json missing a required field`() {
        assertThrows(UpdateMetadataException::class.java) {
            UpdateMetadata.parse("""{"schemaVersion": 1}""")
        }
    }

    @Test
    fun `rejects a sha256 with the wrong length`() {
        val badJson = validJson.replace("a".repeat(64), "a".repeat(10))
        assertThrows(UpdateMetadataException::class.java) {
            UpdateMetadata.parse(badJson)
        }
    }

    @Test
    fun `rejects a sha256 with non-hex characters`() {
        val badJson = validJson.replace("a".repeat(64), "z".repeat(64))
        assertThrows(UpdateMetadataException::class.java) {
            UpdateMetadata.parse(badJson)
        }
    }
}
