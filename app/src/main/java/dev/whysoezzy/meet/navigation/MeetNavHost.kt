package dev.whysoezzy.meet.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import dev.whysoezzy.meet.navigation.routes.authNavigation
import dev.whysoezzy.meet.navigation.routes.communitiesNavigation
import dev.whysoezzy.meet.navigation.routes.meetingsNavigation
import dev.whysoezzy.meet.navigation.routes.profileNavigation

@Composable
fun MeetNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = MeetRoute.Main.route,
        modifier = modifier
    ) {
        authNavigation(navController)
        meetingsNavigation(navController)
        communitiesNavigation(navController)
        profileNavigation(navController)
    }
}
