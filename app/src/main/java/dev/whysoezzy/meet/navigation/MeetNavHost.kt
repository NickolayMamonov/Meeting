package dev.whysoezzy.meet.navigation

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import dev.whysoezzy.meet.navigation.routes.communitiesNavigation
import dev.whysoezzy.meet.navigation.routes.meetingsNavigation
import dev.whysoezzy.meet.navigation.routes.profileNavigation

private const val TAG = "MeetNavHost"

@Composable
fun MeetNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    Log.d(TAG, "MeetNavHost created with startDestination: ${MeetRoute.Main.route}")

    NavHost(
        navController = navController,
        startDestination = MeetRoute.Main.route,
        modifier = modifier
    ) {
        Log.d(TAG, "Setting up navigation graphs")
        // authNavigation(navController) // Temporarily removed
        meetingsNavigation(navController)
        communitiesNavigation(navController)
        profileNavigation(navController)
        Log.d(TAG, "All navigation graphs registered")
    }
}