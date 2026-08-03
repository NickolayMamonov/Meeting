package dev.whysoezzy.meet.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.os.bundleOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.whysoezzy.auth.domain.models.EmailOtpAttemptResult
import org.koin.androidx.compose.koinViewModel

/**
 * The only source that retains the three phone-build destination route strings.
 * These destinations are inert and converge from durable authentication state;
 * restored legacy arguments are never inspected, logged, or forwarded.
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
    LegacyAuthCompatibility.routes.forEach { route ->
        builder.composable(route) {
            val authViewModel: AuthCheckViewModel = koinViewModel()
            val isLoggedIn by authViewModel.isLoggedIn.collectAsStateWithLifecycle()
            val pending by authViewModel.pendingAttempt.collectAsStateWithLifecycle()

            LaunchedEffect(isLoggedIn, pending) {
                val pendingState = pending ?: return@LaunchedEffect
                when {
                    isLoggedIn == true -> navController.navigate(MeetRoute.Main.route) {
                        launchSingleTop = true
                        popUpTo(MeetRoute.Auth.route) { inclusive = true }
                    }
                    pendingState is EmailOtpAttemptResult.Found -> {
                        navController.popBackStack(MeetRoute.Auth.route, false)
                        navController.navigate(
                            MeetRoute.CodeVerification.destinationId,
                            bundleOf("attemptId" to pendingState.attempt.attemptId),
                        )
                    }
                    else -> navController.navigate(MeetRoute.EmailInput.route) {
                        launchSingleTop = true
                        popUpTo(MeetRoute.Auth.route) { inclusive = false }
                    }
                }
            }
        }
    }
}
