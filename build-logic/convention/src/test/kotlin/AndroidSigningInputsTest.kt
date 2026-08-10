import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AndroidSigningInputsTest {
    private val complete =
        mapOf(
            "ANDROID_RELEASE_KEYSTORE_FILE" to "release.p12",
            "ANDROID_RELEASE_STORE_PASSWORD" to "store-password",
            "ANDROID_RELEASE_KEY_ALIAS" to "meet-release",
            "ANDROID_RELEASE_KEY_PASSWORD" to "key-password",
            "ANDROID_RELEASE_CERT_SHA256" to "A".repeat(64),
        )

    @Test
    fun `accepts complete inputs and normalizes fingerprint`() {
        val inputs = AndroidSigningInputs.parse(complete)

        assertEquals("meet-release", inputs.keyAlias)
        assertEquals("a".repeat(64), inputs.normalizedCertificateSha256)
    }

    @Test
    fun `reports every missing signing input without values`() {
        val error =
            assertThrows(IllegalArgumentException::class.java) {
                AndroidSigningInputs.parse(complete - "ANDROID_RELEASE_STORE_PASSWORD" - "ANDROID_RELEASE_KEY_PASSWORD")
            }

        assertTrueContains(error.message, "ANDROID_RELEASE_STORE_PASSWORD")
        assertTrueContains(error.message, "ANDROID_RELEASE_KEY_PASSWORD")
        require(error.message?.contains("store-password") == false)
    }

    @Test
    fun `rejects wrong alias and malformed fingerprint`() {
        assertThrows(IllegalArgumentException::class.java) {
            AndroidSigningInputs.parse(complete + ("ANDROID_RELEASE_KEY_ALIAS" to "androiddebugkey"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AndroidSigningInputs.parse(complete + ("ANDROID_RELEASE_CERT_SHA256" to "not-a-fingerprint"))
        }
    }

    private fun assertTrueContains(
        actual: String?,
        expected: String,
    ) {
        require(actual?.contains(expected) == true) {
            "Expected '$actual' to contain '$expected'."
        }
    }
}
