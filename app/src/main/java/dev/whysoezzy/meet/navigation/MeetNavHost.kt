package dev.whysoezzy.meet.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.whysoezzy.auth.domain.models.AuthSession
import com.whysoezzy.auth.domain.models.EmailOtpAttemptResult
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
    val durableSession by authViewModel.durableSession.collectAsStateWithLifecycle()

    // Показываем SplashScreen пока проверяем авторизацию
    if (isLoggedIn == null || pendingAttempt == null || durableSession == null) {
//        SplashScreen()
        return
    }

    MeetNavHostContent(
        navController = navController,
        isLoggedIn = requireNotNull(isLoggedIn),
        pendingAttempt = requireNotNull(pendingAttempt),
        durableSession = requireNotNull(durableSession),
        modifier = modifier,
    )
}

@Composable
internal fun MeetNavHostContent(
    navController: NavHostController,
    isLoggedIn: Boolean,
    pendingAttempt: EmailOtpAttemptResult,
    durableSession: AuthSession? = null,
    modifier: Modifier = Modifier,
) {
    // NavHost remembers its graph by the builder lambda. Keep mutable recovery values behind
    // stable state holders so clearing a recovered attempt cannot replace the graph and its
    // onboarding back stack.
    val latestIsLoggedIn = rememberUpdatedState(isLoggedIn)
    val latestPendingAttempt = rememberUpdatedState(pendingAttempt)
    val graphBuilder: NavGraphBuilder.() -> Unit = remember(navController) {
        {
            authNavigation(
                navController = navController,
                onLegacyRedirectRequested = {
                    redirectLegacyAuth(
                        navController = navController,
                        isLoggedIn = latestIsLoggedIn.value,
                        pendingAttempt = latestPendingAttempt.value,
                    )
                },
            )
            meetingsNavigation(navController)
            communitiesNavigation(navController)
            profileNavigation(navController)
        }
    }

    NavHost(
        navController = navController,
        // Keep the root graph identity stable. Authentication changes are explicit
        // navigation events; rebuilding this graph would discard auth onboarding.
        startDestination = MeetRoute.Auth.route,
        modifier = modifier,
        builder = graphBuilder,
    )
    LegacyAuthCompatibility.assertIds(navController.graph)

    AuthStateNavigationEffect(
        navController = navController,
        isLoggedIn = isLoggedIn,
        durableSession = durableSession,
    )
}

@Composable
internal fun AuthStateNavigationEffect(
    navController: NavHostController,
    isLoggedIn: Boolean?,
    durableSession: AuthSession? = null,
) {
    LaunchedEffect(isLoggedIn, durableSession) {
        if (durableSession != null) {
            navController.resolveFromDurableSession(durableSession)
            return@LaunchedEffect
        }
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
