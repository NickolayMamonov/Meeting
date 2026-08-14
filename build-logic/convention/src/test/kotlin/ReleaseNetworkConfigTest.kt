import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNetworkConfigTest {
    @Test
    fun `accepts only the production origin and generates system CA config`() {
        val config = ReleaseNetworkConfig.parse("https://api.whysoezzy.online")

        assertEquals("https://api.whysoezzy.online", config.baseUrl)
        assertEquals("api.whysoezzy.online", config.host)
        val xml = config.networkSecurityConfigXml()
        assertTrue(xml.contains("""<domain includeSubdomains="false">api.whysoezzy.online</domain>"""))
        assertTrue(xml.contains("""<certificates src="system" />"""))
        assertTrue(xml.contains("""cleartextTrafficPermitted="false""""))
        assertFalse(xml.contains("pin-set"))
        assertFalse(xml.contains("<pin"))
    }

    @Test
    fun `rejects every non exact release URL`() {
        listOf(
            null,
            "",
            " ",
            "http://api.whysoezzy.online",
            "https://api.whysoezzy.online/",
            "https://api.whysoezzy.online/path",
            "https://api.whysoezzy.online?query=1",
            "https://api.whysoezzy.online#fragment",
            "https://user:password@api.whysoezzy.online",
            "https://api.whysoezzy.online:443",
            "https://www.api.whysoezzy.online",
            "https://api.example.com",
            "https://release-test.invalid",
            "https://localhost",
            "https://10.0.2.2",
        ).forEach { value ->
            assertThrows(value, IllegalArgumentException::class.java) {
                ReleaseNetworkConfig.parse(value)
            }
        }
    }
}
