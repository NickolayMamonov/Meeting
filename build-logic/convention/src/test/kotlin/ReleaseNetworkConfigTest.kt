import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class ReleaseNetworkConfigTest {
    private val firstPin = Base64.getEncoder().encodeToString(ByteArray(32) { (it + 1).toByte() })
    private val secondPin = Base64.getEncoder().encodeToString(ByteArray(32) { (it + 33).toByte() })

    @Test
    fun `normalizes host and generates exact pinned HTTPS resource`() {
        val config =
            ReleaseNetworkConfig.parse(
                "https://API.whysoezzy.dev/v1/",
                "$firstPin\r\n$secondPin",
            )

        assertEquals("api.whysoezzy.dev", config.host)
        val xml = config.networkSecurityConfigXml()
        assertTrue(xml.contains("""<domain includeSubdomains="false">api.whysoezzy.dev</domain>"""))
        assertTrue(xml.contains("""<pin digest="SHA-256">$firstPin</pin>"""))
        assertTrue(xml.contains("""<pin digest="SHA-256">$secondPin</pin>"""))
        assertTrue(xml.contains("""cleartextTrafficPermitted="false""""))
        assertFalse(xml.contains("expiration="))
    }

    @Test
    fun `rejects missing insecure credentialed and placeholder URLs`() {
        listOf(
            null,
            "",
            "http://api.whysoezzy.dev",
            "https://user:password@api.whysoezzy.dev",
            "https://api.example.com",
            "https://release-test.invalid",
            "https://localhost",
            "https://10.0.2.2",
            "https://192.0.2.1",
            "https://api.whysoezzy.dev/#fragment",
        ).forEach { url ->
            assertThrows(
                url,
                IllegalArgumentException::class.java,
            ) {
                ReleaseNetworkConfig.parse(url, "$firstPin\n$secondPin")
            }
        }
    }

    @Test
    fun `rejects weak malformed duplicate and placeholder pins`() {
        val placeholder = Base64.getEncoder().encodeToString(ByteArray(32))
        listOf(
            null,
            "",
            firstPin,
            "$firstPin\n$firstPin",
            "$firstPin\nnot-base64",
            "$firstPin\n${Base64.getEncoder().encodeToString(ByteArray(31) { 1 })}",
            "$firstPin\n$placeholder",
            "$firstPin\n $secondPin",
            "$firstPin\n$secondPin\n",
        ).forEach { pins ->
            assertThrows(
                pins,
                IllegalArgumentException::class.java,
            ) {
                ReleaseNetworkConfig.parse("https://api.whysoezzy.dev", pins)
            }
        }
    }
}
