package dev.whysoezzy.meet.navigation.routes

import android.util.Log
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import dev.whysoezzy.meet.navigation.MeetRoute
import dev.whysoezzy.meetings.MainScreen
import dev.whysoezzy.meetings.details.presentation.MeetingDetailsScreen
import dev.whysoezzy.meetings.participants.presentation.MeetingParticipantsScreen

private const val TAG = "MeetingsNavigation"

fun NavGraphBuilder.meetingsNavigation(navController: NavController) {

    composable(MeetRoute.Main.route) {
        Log.d(TAG, "MainScreen composable created")
        MainScreen(
            onMeetingClick = { meetingId ->
                navController.navigate(MeetRoute.MeetingDetails.createRoute(meetingId))
            },
            onCommunityClick = { communityId ->
                navController.navigate(MeetRoute.CommunityDetails.createRoute(communityId))
            },
            onProfileClick = {
                navController.navigate(MeetRoute.Profile.route)
            },
            onUserProfileClick = { userId ->
                navController.navigate(MeetRoute.UserProfile.createRoute(userId))
            }
        )
    }

    composable(
        route = MeetRoute.MeetingDetails.route,
        arguments = listOf(navArgument("meetingId") { type = NavType.LongType })
    ) { backStackEntry ->
        val meetingId = backStackEntry.arguments?.getLong("meetingId") ?: 0L
        MeetingDetailsScreen(
            meetingId = meetingId,
            onBackPressed = { navController.popBackStack() },
            onParticipantsClick = {
                navController.navigate(MeetRoute.MeetingParticipants.createRoute(meetingId))
            },
            onCommunityClick = { communityId ->
                navController.navigate(MeetRoute.CommunityDetails.createRoute(communityId))
            },
            onHostClick = { userId ->
                navController.navigate(MeetRoute.UserProfile.createRoute(userId))
            },
            onUserProfileClick = { userId ->
                navController.navigate(MeetRoute.UserProfile.createRoute(userId))
            },
            onOtherMeetingClick = { otherMeetingId ->
                navController.navigate(MeetRoute.MeetingDetails.createRoute(otherMeetingId))
            }
        )
    }

    composable(
        route = MeetRoute.MeetingParticipants.route,
        arguments = listOf(navArgument("meetingId") { type = NavType.LongType })
    ) { backStackEntry ->
        val meetingId = backStackEntry.arguments?.getLong("meetingId") ?: 0L
        MeetingParticipantsScreen(
            meetingId = meetingId,
            onBackPressed = { navController.popBackStack() },
            onPersonClick = { userId ->
                navController.navigate(MeetRoute.UserProfile.createRoute(userId))
            }
        )
    }
}
