package dev.whysoezzy.meet.navigation.routes

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import dev.whysoezzy.communities.details.presentation.CommunityDetailsScreen
import dev.whysoezzy.communities.subscribers.CommunitySubscribersScreen
import dev.whysoezzy.meet.navigation.MeetRoute

fun NavGraphBuilder.communitiesNavigation(navController: NavController) {

    composable(
        route = MeetRoute.CommunityDetails.route,
        arguments = listOf(navArgument("communityId") { type = NavType.LongType })
    ) { backStackEntry ->
        val communityId = backStackEntry.arguments?.getLong("communityId") ?: 0L
        CommunityDetailsScreen(
            communityId = communityId,
            onBackPressed = { navController.popBackStack() },
            onSubscribersClick = {
                navController.navigate(MeetRoute.CommunitySubscribers.createRoute(communityId))
            },
            onMeetingClick = { meetingId ->
                navController.navigate(MeetRoute.MeetingDetails.createRoute(meetingId))
            },
            onUserProfileClick = { userId ->
                navController.navigate(MeetRoute.UserProfile.createRoute(userId))
            }
        )
    }

    composable(
        route = MeetRoute.CommunitySubscribers.route,
        arguments = listOf(navArgument("communityId") { type = NavType.LongType })
    ) { backStackEntry ->
        val communityId = backStackEntry.arguments?.getLong("communityId") ?: 0L
        CommunitySubscribersScreen(
            communityId = communityId,
            onBackPressed = { navController.popBackStack() },
            onPersonClick = { userId ->
                navController.navigate(MeetRoute.UserProfile.createRoute(userId))
            }
        )
    }
}
