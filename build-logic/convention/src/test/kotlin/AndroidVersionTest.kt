import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AndroidVersionTest {
    @Test
    fun `parses canonical stable version and computes code`() {
        val version = AndroidVersion.parseJson("""{ "version": "1.0.0" }""")

        assertEquals("1.0.0", version.name)
        assertEquals(1_000_000, version.code)
    }

    @Test
    fun `accepts minimum and maximum supported versions`() {
        assertEquals(1, AndroidVersion.parse("0.0.1").code)
        assertEquals(2_099_999_999, AndroidVersion.parse("2099.999.999").code)
    }

    @Test
    fun `version codes are monotonic and collision free at component boundaries`() {
        val versions =
            listOf(
                AndroidVersion.parse("1.2.998"),
                AndroidVersion.parse("1.2.999"),
                AndroidVersion.parse("1.3.0"),
                AndroidVersion.parse("2.0.0"),
            )

        assertEquals(versions.map { it.code }.sorted(), versions.map { it.code })
        assertEquals(versions.size, versions.map { it.code }.distinct().size)
        assertNotEquals(AndroidVersion.parse("1.2.3").code, AndroidVersion.parse("1.3.2").code)
    }

    @Test
    fun `rejects zero overflow and noncanonical stable versions`() {
        listOf(
            "0.0.0",
            "2100.0.0",
            "1.1000.0",
            "1.0.1000",
            "01.0.0",
            "1.00.0",
            "1.0.00",
            "1.0",
            "1.0.0-alpha",
            "1.0.0+build",
            " 1.0.0",
            "1.0.0 ",
            "999999999999999999999.0.0",
        ).forEach { value ->
            assertThrows(value, IllegalArgumentException::class.java) {
                AndroidVersion.parse(value)
            }
        }
    }

    @Test
    fun `rejects unsupported version json shapes`() {
        listOf(
            "{}",
            """{"version":1}""",
            """{"version":"1.0.0","other":true}""",
            """{"other":true,"version":"1.0.0"}""",
            """{"version":"1.0.0","version":"1.0.1"}""",
            """{"version":"1.0.0\n"}""",
            """["1.0.0"]""",
            "not-json",
        ).forEach { json ->
            assertThrows(json, IllegalArgumentException::class.java) {
                AndroidVersion.parseJson(json)
            }
        }
    }
}
