package dev.whysoezzy.meet.navigation.routes

import androidx.core.os.bundleOf
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import dev.whysoezzy.auth.presentation.code.CodeVerificationScreen
import dev.whysoezzy.auth.presentation.email.EmailInputScreen
import dev.whysoezzy.auth.presentation.name.NameInputScreen
import dev.whysoezzy.auth.presentation.success.AuthSuccessScreen
import dev.whysoezzy.meet.navigation.MeetRoute
import dev.whysoezzy.meet.navigation.registerLegacyAuthCompatibilityDestinations

fun NavGraphBuilder.authNavigation(
    navController: NavController,
    startDestination: String = MeetRoute.EmailInput.route,
    onLegacyDestinationComposed: ((Int) -> Unit)? = null,
    onLegacyRedirectRequested: (() -> Unit)? = null,
) {
    navigation(
        startDestination = startDestination,
        route = MeetRoute.Auth.route,
    ) {
        composable(MeetRoute.EmailInput.route) {
            EmailInputScreen(
                onAttemptStarted = { attemptId ->
                    navController.navigate(
                        MeetRoute.CodeVerification.destinationId,
                        bundleOf("attemptId" to attemptId),
                    )
                },
                onBackPressed = { navController.popBackStack() },
            )
        }

        composable(
            route = MeetRoute.CodeVerification.route,
            arguments = listOf(
                androidx.navigation.navArgument("attemptId") {
                    type = NavType.StringType
                },
            ),
        ) { backStackEntry ->
            val attemptId = backStackEntry.arguments?.getString("attemptId").orEmpty()
            CodeVerificationScreen(
                attemptId = attemptId,
                onCodeVerifiedExisting = {
                    navController.navigate(MeetRoute.Main.route) {
                        popUpTo(MeetRoute.Auth.route) { inclusive = true }
                    }
                },
                onCodeVerifiedNew = {
                    navController.navigate(MeetRoute.NameInput.route) {
                        popUpTo(MeetRoute.Auth.route) { inclusive = false }
                    }
                },
                onBackPressed = { navController.popBackStack() },
            )
        }

        composable(MeetRoute.NameInput.route) {
            NameInputScreen(
                onNameSubmitted = {
                    navController.navigate(MeetRoute.AuthSuccess.route)
                },
                onBackPressed = { navController.popBackStack() },
            )
        }

        composable(MeetRoute.AuthSuccess.route) {
            AuthSuccessScreen(
                onContinueClicked = {
                    navController.navigate(MeetRoute.Main.route) {
                        popUpTo(MeetRoute.Auth.route) { inclusive = true }
                    }
                },
            )
        }

        registerLegacyAuthCompatibilityDestinations(
            builder = this,
            navController = navController,
            onLegacyDestinationComposed = onLegacyDestinationComposed,
            onLegacyRedirectRequested = onLegacyRedirectRequested,
        )
    }
}
