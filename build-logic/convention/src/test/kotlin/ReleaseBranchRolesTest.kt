import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class ReleaseBranchRolesTest {
    @Test
    fun `parses immutable typed authorities and derives head refs`() {
        val roles = ReleaseBranchRoles.parseJson(validJson)

        assertEquals(ReleaseBranchRole.INTEGRATION, roles.integration.role)
        assertEquals("dev", roles.integration.branch)
        assertEquals("refs/heads/dev", roles.integration.headRef)
        assertEquals(ReleaseBranchRole.STABLE, roles.stable.role)
        assertEquals("master", roles.authority(ReleaseBranchRole.STABLE).branch)
        assertEquals("refs/heads/master", roles.stable.headRef)
    }

    @Test
    fun `parses the shared valid release roles fixture`() {
        val roles = ReleaseBranchRoles.parseJson(sharedFixture("valid.json"))

        assertEquals("dev", roles.integration.branch)
        assertEquals("master", roles.stable.branch)
    }

    @Test
    fun `rejects every shared duplicate member fixture`() {
        val fixtures =
            Files
                .list(sharedFixtureDirectory())
                .use { paths ->
                    paths
                        .filter { it.fileName.toString().startsWith("duplicate-") }
                        .sorted()
                        .toList()
                }
        require(fixtures.size >= 11) {
            "Expected the complete shared duplicate fixture corpus."
        }

        fixtures.forEach { fixture ->
            assertThrows(fixture.fileName.toString(), IllegalArgumentException::class.java) {
                ReleaseBranchRoles.parseJson(Files.readString(fixture))
            }
        }
    }

    @Test
    fun `accepts canonical nested branch names`() {
        val roles =
            ReleaseBranchRoles.parseJson(
                rolesJson(
                    integration = "release/2026.08",
                    stable = "production/v1",
                ),
            )

        assertEquals("release/2026.08", roles.integration.branch)
        assertEquals("refs/heads/production/v1", roles.stable.headRef)
    }

    @Test
    fun `rejects unsupported schema shapes and branch assignments`() {
        listOf(
            """{"schemaVersion":2,"roles":{"integration":{"branch":"dev"},"stable":{"branch":"master"}}}""",
            """{"schemaVersion":1,"roles":{"integration":{"branch":"dev"},"stable":{"branch":"master"},"extra":{"branch":"other"}}}""",
            """{"schemaVersion":1,"roles":{"integration":{"branch":"dev"},"stable":{"branch":"dev"}}}""",
            """{"schemaVersion":1,"roles":{"integration":{"branch":"refs/heads/dev"},"stable":{"branch":"master"}}}""",
            rolesJson(integration = "dev/.hidden", stable = "master"),
            rolesJson(integration = "dev/./child", stable = "master"),
            rolesJson(integration = "dev/foo.lock", stable = "master"),
            rolesJson(integration = "dev//child", stable = "master"),
            rolesJson(integration = "dev/child..name", stable = "master"),
            rolesJson(integration = "dev/child@{name}", stable = "master"),
            rolesJson(integration = "dev/", stable = "master"),
            rolesJson(integration = "dev.", stable = "master"),
            rolesJson(integration = "dev", stable = "master/.lock/child"),
        ).forEach { json ->
            assertThrows(json, IllegalArgumentException::class.java) {
                ReleaseBranchRoles.parseJson(json)
            }
        }
    }

    @Test
    fun `rejects duplicate members before tree validation including escaped names`() {
        listOf(
            """{"schemaVersion":1,"schemaVersion":1,"roles":{"integration":{"branch":"dev"},"stable":{"branch":"master"}}}""",
            """{"schemaVersion":1,"roles":{},"roles":{"integration":{"branch":"dev"},"stable":{"branch":"master"}}}""",
            """{"schemaVersion":1,"roles":{"integration":{"branch":"dev"},"integration":{"branch":"other"},"stable":{"branch":"master"}}}""",
            """{"schemaVersion":1,"roles":{"integration":{"branch":"dev","branch":"dev"},"stable":{"branch":"master"}}}""",
            """{"schemaVersion":1,"roles":{"integration":{"branch":"dev"},"stable":{"branch":"master"}},"sche\u006daVersion":1}""",
        ).forEach { json ->
            assertThrows(json, IllegalArgumentException::class.java) {
                ReleaseBranchRoles.parseJson(json)
            }
        }
    }

    @Test
    fun `rejects malformed json and non-string or missing values`() {
        listOf(
            "",
            "not-json",
            """[]""",
            """{"schemaVersion":"1","roles":{"integration":{"branch":"dev"},"stable":{"branch":"master"}}}""",
            """{"schemaVersion":1,"roles":{"integration":{"branch":1},"stable":{"branch":"master"}}}""",
            """{"schemaVersion":1,"roles":{"integration":{},"stable":{"branch":"master"}}}""",
            """{"schemaVersion":1,"roles":{"integration":{"branch":"dev"},"stable":{"branch":"master",}}}""",
            """{"schemaVersion":1,"roles":{"integration":{"branch":"dev"},"stable":{"branch":"master"}}""",
        ).forEach { json ->
            assertThrows(json, IllegalArgumentException::class.java) {
                ReleaseBranchRoles.parseJson(json)
            }
        }
    }

    private fun rolesJson(
        integration: String,
        stable: String,
    ): String =
        """{"schemaVersion":1,"roles":{"integration":{"branch":"$integration"},"stable":{"branch":"$stable"}}}"""

    private companion object {
        fun sharedFixture(name: String): String =
            Files.readString(sharedFixtureDirectory().resolve(name))

        fun sharedFixtureDirectory(): Path {
            var directory = Path.of("").toAbsolutePath().normalize()
            while (true) {
                val candidate = directory.resolve("scripts/release/fixtures/release-roles")
                if (Files.isDirectory(candidate)) {
                    return candidate
                }
                directory =
                    directory.parent
                        ?: error("Could not locate shared release-role fixtures.")
            }
        }

        const val validJson =
            """{
                "schemaVersion": 1,
                "roles": {
                    "integration": {"branch": "dev"},
                    "stable": {"branch": "master"}
                }
            }"""
    }
}
