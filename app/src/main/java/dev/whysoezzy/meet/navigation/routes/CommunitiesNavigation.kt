package dev.whysoezzy.meet.navigation.routes

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.whysoezzy.communities.details.presentation.CommunityDetailsScreen
import dev.whysoezzy.communities.subscribers.presentation.CommunitySubscribersScreen
import dev.whysoezzy.meet.navigation.MeetRoute

fun NavGraphBuilder.communitiesNavigation(navController: NavController) {
    composable(MeetRoute.CommunityDetails.route) { backStackEntry ->
        val communityId = backStackEntry.arguments?.getString("communityId")?.toLongOrNull() ?: 0L
        CommunityDetailsScreen(
            communityId = communityId,
            onBackPressed = {
                navController.popBackStack()
            },
            onSubscribersClick = {
                navController.navigate(MeetRoute.CommunitySubscribers.createRoute(communityId))
            }
        )
    }

    composable(MeetRoute.CommunitySubscribers.route) { backStackEntry ->
        val communityId = backStackEntry.arguments?.getString("communityId")?.toLongOrNull() ?: 0L
        CommunitySubscribersScreen(
            communityId = communityId,
            onBackPressed = {
                navController.popBackStack()
            }
        )
    }
}