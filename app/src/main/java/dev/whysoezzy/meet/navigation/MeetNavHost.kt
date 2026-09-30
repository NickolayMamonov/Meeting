package dev.whysoezzy.meet.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    onReady: () -> Unit = {},
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
        onReady = onReady,
    )
}

@Composable
internal fun MeetNavHostContent(
    navController: NavHostController,
    isLoggedIn: Boolean,
    pendingAttempt: EmailOtpAttemptResult,
    durableSession: AuthSession? = null,
    modifier: Modifier = Modifier,
    onReady: () -> Unit = {},
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
    SideEffect(onReady)

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
    LaunchedEffect(isLoggedIn) {
        when {
            isLoggedIn == false -> {
                if (navController.currentDestination?.route != MeetRoute.EmailInput.route) {
                    navController.navigate(MeetRoute.EmailInput.route) {
                        popUpTo(navController.graph.id)
                        launchSingleTop = true
                    }
                }
            }

            isLoggedIn == true && durableSession != null -> {
                // Durable stage owns cold-start routing. Once a live flow has started,
                // feature events own ordinary onboarding transitions.
                navController.resolveFromDurableSession(durableSession)
            }
        }
    }
}
