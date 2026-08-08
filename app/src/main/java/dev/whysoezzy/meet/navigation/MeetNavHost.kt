package dev.whysoezzy.meet.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import dev.whysoezzy.meet.navigation.routes.authNavigation
import dev.whysoezzy.meet.navigation.routes.communitiesNavigation
import dev.whysoezzy.meet.navigation.routes.meetingsNavigation
import dev.whysoezzy.meet.navigation.routes.profileNavigation
import org.koin.androidx.compose.koinViewModel

@Composable
fun MeetNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val authViewModel: AuthCheckViewModel = koinViewModel()
    val isLoggedIn by authViewModel.isLoggedIn.collectAsStateWithLifecycle()
    val pendingAttempt by authViewModel.pendingAttempt.collectAsStateWithLifecycle()

    // Показываем SplashScreen пока проверяем авторизацию
    if (isLoggedIn == null || pendingAttempt == null) {
//        SplashScreen()
        return
    }

    val recoveredPendingAttempt = requireNotNull(pendingAttempt)

    NavHost(
        navController = navController,
        // Keep the root graph identity stable. Authentication changes are explicit
        // navigation events; rebuilding this graph would discard auth onboarding.
        startDestination = MeetRoute.Auth.route,
        modifier = modifier,
    ) {
        authNavigation(
            navController = navController,
            onLegacyRedirectRequested = {
                redirectLegacyAuth(
                    navController = navController,
                    isLoggedIn = requireNotNull(isLoggedIn),
                    pendingAttempt = recoveredPendingAttempt,
                )
            },
        )
        meetingsNavigation(navController)
        communitiesNavigation(navController)
        profileNavigation(navController)
    }
    LegacyAuthCompatibility.assertIds(navController.graph)

    AuthStateNavigationEffect(
        navController = navController,
        isLoggedIn = isLoggedIn,
    )
}

@Composable
internal fun AuthStateNavigationEffect(
    navController: NavHostController,
    isLoggedIn: Boolean?,
) {
    LaunchedEffect(isLoggedIn) {
        when {
            isLoggedIn == false -> {
                if (!navController.currentDestination.isInAuthGraph()) {
                    navController.navigate(MeetRoute.Auth.route) {
                        // A recreated controller can restore Main/meeting/profile entries
                        // even though the graph's start destination is Auth.
                        popUpTo(navController.graph.id)
                        launchSingleTop = true
                    }
                }
            }

            isLoggedIn == true &&
                navController.currentDestination?.route == MeetRoute.EmailInput.route -> {
                navController.navigate(MeetRoute.Main.route) {
                    popUpTo(MeetRoute.Auth.route) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }
}

private fun NavDestination?.isInAuthGraph(): Boolean =
    this?.hierarchy?.any { it.route == MeetRoute.Auth.route } == true
