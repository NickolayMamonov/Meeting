package dev.whysoezzy.meet.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavController
import androidx.navigation.NavGraph
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/**
 * The only source that retains the three phone-build destination route strings.
 * These destinations are inert aliases to the current email entry screen;
 * restored legacy arguments are never inspected, logged, or forwarded.
 */
internal object LegacyAuthCompatibility {
    val routes: List<String> = listOf(
        "auth/phone",
        "auth/code/{phoneNumber}",
        "auth/name/{phone}/{code}",
    )

    fun assertIds(graph: NavGraph) {
        val authGraph =
            graph.findNode(navigationDestinationId(MeetRoute.Auth.route)) as? NavGraph
                ?: error("Missing auth graph")
        routes.forEach { route ->
            val destination = authGraph.findNode(navigationDestinationId(route))
                ?: error("Missing legacy compatibility destination: $route")
            check(destination.id == navigationDestinationId(route)) {
                "Legacy destination ID changed for $route"
            }
            check(destination.route == route) {
                "Legacy destination route changed for $route: ${destination.route}"
            }
        }
        val activeCode = authGraph.findNode(MeetRoute.CodeVerification.destinationId)
            ?: error("Missing active code destination")
        check(activeCode.id == MeetRoute.CodeVerification.destinationId) {
            "Active code destination ID changed"
        }
        check(activeCode.id !in routes.map(::navigationDestinationId)) {
            "The active code destination collides with a legacy destination ID"
        }
    }
}

internal fun redirectLegacyAuth(navController: NavController) {
    navController.navigate(MeetRoute.EmailInput.route) {
        launchSingleTop = true
        popUpTo(MeetRoute.Auth.route) { inclusive = false }
    }
}

internal fun registerLegacyAuthCompatibilityDestinations(
    builder: NavGraphBuilder,
    navController: NavController,
    onLegacyDestinationComposed: ((Int) -> Unit)? = null,
) {
    LegacyAuthCompatibility.routes.forEach { route ->
        builder.composable(route) {
            onLegacyDestinationComposed?.invoke(navigationDestinationId(route))
            LaunchedEffect(Unit) {
                redirectLegacyAuth(navController)
            }
        }
    }
}
