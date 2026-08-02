package dev.whysoezzy.meet.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraph
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/**
 * The only source that retains the three phone-build destination route strings.
 *
 * These destinations are deliberately inert. They never receive a back-stack entry,
 * never inspect restored arguments, and immediately converge on the durable email
 * entry state. Keeping the routes here protects Navigation's saved destination IDs
 * during direct upgrades from the phone build.
 */
internal object LegacyAuthCompatibility {
    val routes: List<String> = listOf(
        "auth/phone",
        "auth/code/{phoneNumber}",
        "auth/name/{phone}/{code}",
    )

    fun assertIds(graph: NavGraph) {
        routes.forEach { route ->
            val destination = graph.findNode(route.hashCode())
                ?: error("Missing legacy compatibility destination: $route")
            check(destination.id == route.hashCode()) {
                "Legacy destination ID changed for $route"
            }
        }
        check("auth/code/{attemptId}".hashCode() !in routes.map(String::hashCode)) {
            "The active code destination collides with a legacy destination ID"
        }
    }
}

internal fun registerLegacyAuthCompatibilityDestinations(
    builder: NavGraphBuilder,
    navController: NavController,
) {
    builder.composable(LegacyAuthCompatibility.routes[0]) {
        redirectToEmail(navController)
    }
    builder.composable(LegacyAuthCompatibility.routes[1]) {
        redirectToEmail(navController)
    }
    builder.composable(LegacyAuthCompatibility.routes[2]) {
        redirectToEmail(navController)
    }
}

private fun redirectToEmail(navController: NavController) {
    navController.navigate(MeetRoute.EmailInput.route) {
        launchSingleTop = true
        restoreState = false
        popUpTo(MeetRoute.Auth.route) {
            inclusive = false
            saveState = false
        }
    }
}
