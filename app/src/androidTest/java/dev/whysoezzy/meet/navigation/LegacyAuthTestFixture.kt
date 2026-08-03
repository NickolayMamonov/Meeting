package dev.whysoezzy.meet.navigation

internal data class LegacyStackFixture(
    val route: String,
    val navigationPath: List<String>,
    val destinationId: Int,
    val argumentNames: Set<String>,
)

internal object LegacyAuthTestFixture {
    const val authGraphId = 2027237045

    val stacks =
        listOf(
            LegacyStackFixture(
                route = "auth/phone",
                navigationPath = emptyList(),
                destinationId = -518941292,
                argumentNames = emptySet(),
            ),
            LegacyStackFixture(
                route = "auth/code/{phoneNumber}",
                navigationPath = listOf("auth/code/old-phone"),
                destinationId = -2076142087,
                argumentNames = setOf("phoneNumber"),
            ),
            LegacyStackFixture(
                route = "auth/name/{phone}/{code}",
                navigationPath = listOf(
                    "auth/code/old-phone",
                    "auth/name/old-phone/old-code",
                ),
                destinationId = -1552147740,
                argumentNames = setOf("phone", "code"),
            ),
        )
}
