package dev.whysoezzy.meet.navigation.routes

import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.os.bundleOf
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.whysoezzy.auth.domain.models.AuthSession
import com.whysoezzy.auth.domain.repository.AuthSessionRepository
import dev.whysoezzy.auth.presentation.code.CodeVerificationScreen
import dev.whysoezzy.auth.presentation.email.EmailInputScreen
import dev.whysoezzy.auth.presentation.name.NameInputMode
import dev.whysoezzy.auth.presentation.name.NameInputScreen
import dev.whysoezzy.auth.presentation.success.AuthSuccessScreen
import dev.whysoezzy.meet.navigation.MeetRoute
import dev.whysoezzy.meet.navigation.registerLegacyAuthCompatibilityDestinations
import dev.whysoezzy.meet.navigation.resolveFromDurableSession
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

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
                mode = NameInputMode.Onboarding,
                onNameSubmitted = {
                    navController.navigate(MeetRoute.AuthSuccess.route)
                },
                onProfileCompleted = {},
                onResolveFromDurableSession = navController::resolveFromDurableSession,
                onBackPressed = { navController.popBackStack() },
            )
        }

        composable(MeetRoute.AuthSuccess.route) {
            val sessionRepository: AuthSessionRepository = koinInject()
            val scope = rememberCoroutineScope()
            AuthSuccessScreen(
                onContinueClicked = {
                    scope.launch {
                        if (sessionRepository.compareAndSetStage(
                                expected = AuthSession.Stage.Welcome,
                                next = AuthSession.Stage.Ready,
                            )
                        ) {
                            navController.navigate(MeetRoute.Main.route) {
                                popUpTo(MeetRoute.Auth.route) { inclusive = true }
                            }
                        } else {
                            navController.resolveFromDurableSession(sessionRepository.read())
                        }
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
