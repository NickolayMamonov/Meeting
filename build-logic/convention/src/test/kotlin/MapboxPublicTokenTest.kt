import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class MapboxPublicTokenTest {
    @Test
    fun `accepts only public pk tokens with safe serialization characters`() {
        assertEquals("pk.test_token-1.2", MapboxPublicToken.safeBuildValue("pk.test_token-1.2"))
        listOf(null, "", " ", "sk.secret", "pk.", "pk.bad token", "pk.bad\\token", "pk.bad\"token", "pk.bad\ntoken")
            .forEach { assertNull(MapboxPublicToken.safeBuildValue(it)) }
    }

    @Test
    fun `release validation rejects missing and malformed values without echoing token`() {
        listOf(null, "", "sk.secret", "pk.bad token", "pk.bad\ntoken").forEach { token ->
            val exception =
                assertThrows(IllegalArgumentException::class.java) {
                    MapboxPublicToken.validateReleaseValue(token)
                }
            assertEquals(false, exception.message.orEmpty().contains("secret"))
        }
    }

    @Test
    fun `release validation accepts safe public token`() {
        assertEquals(
            "pk.release_token-1",
            MapboxPublicToken.validateReleaseValue("pk.release_token-1"),
        )
    }
}
