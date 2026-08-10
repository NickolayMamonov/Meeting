import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SnapshotVersionTest {
    private val stable = AndroidVersion.parse("1.0.0")
    private val sha = "0123456789abcdef0123456789abcdef01234567"

    @Test
    fun `uses exact run number as version code and attempt only in name`() {
        val first = SnapshotVersion.parse(stable, "12345", "1", sha)
        val retry = SnapshotVersion.parse(stable, "12345", "2", sha)

        assertEquals(12_345, first.code)
        assertEquals(12_345, retry.code)
        assertEquals("1.0.0-snapshot.12345.2+0123456789ab", retry.name)
    }

    @Test
    fun `accepts maximum Android workflow run number`() {
        assertEquals(
            2_100_000_000,
            SnapshotVersion.parse(stable, "2100000000", "1", sha).code,
        )
    }

    @Test
    fun `rejects malformed workflow provenance`() {
        val invalidInputs =
            listOf(
                Triple("0", "1", sha),
                Triple("2100000001", "1", sha),
                Triple("01", "1", sha),
                Triple("1", "0", sha),
                Triple("1", "01", sha),
                Triple("1", "1", sha.uppercase()),
                Triple("1", "1", sha.dropLast(1)),
            )

        invalidInputs.forEach { (run, attempt, commit) ->
            assertThrows(IllegalArgumentException::class.java) {
                SnapshotVersion.parse(stable, run, attempt, commit)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            SnapshotVersion.parse(stable, null, "1", sha)
        }
    }
}
