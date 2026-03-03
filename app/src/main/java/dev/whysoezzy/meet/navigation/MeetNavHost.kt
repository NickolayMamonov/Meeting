package dev.whysoezzy.meet.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.whysoezzy.auth.TokenManager
import dev.whysoezzy.meet.navigation.routes.authNavigation
import dev.whysoezzy.meet.navigation.routes.communitiesNavigation
import dev.whysoezzy.meet.navigation.routes.meetingsNavigation
import dev.whysoezzy.meet.navigation.routes.profileNavigation
import org.koin.compose.koinInject

@Composable
fun MeetNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val tokenManager: TokenManager = koinInject()
    val startDestination = if (tokenManager.isLoggedIn()) {
        MeetRoute.Main.route
    } else {
        MeetRoute.Auth.route
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        authNavigation(navController)
        meetingsNavigation(navController)
        communitiesNavigation(navController)
        profileNavigation(navController)
    }
}
