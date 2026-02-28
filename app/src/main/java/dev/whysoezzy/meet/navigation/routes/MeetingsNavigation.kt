package dev.whysoezzy.meet.navigation.routes

import android.util.Log
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
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
                Log.d(TAG, "Meeting clicked: $meetingId")
                navController.navigate(MeetRoute.MeetingDetails.createRoute(meetingId))
            },
            onCommunityClick = { communityId ->
                Log.d(TAG, "Community clicked: $communityId")
                navController.navigate(MeetRoute.CommunityDetails.createRoute(communityId))
            },
            onProfileClick = {
                Log.d(TAG, "Profile clicked! Navigating to: ${MeetRoute.Profile.route}")
                navController.navigate(MeetRoute.Profile.route)
                Log.d(TAG, "Navigation command sent")
            },
            onUserProfileClick = { userId ->
                navController.navigate(MeetRoute.UserProfile.createRoute(userId))
            }
        )
    }

    composable(MeetRoute.MeetingDetails.route) { backStackEntry ->
        val meetingId = backStackEntry.arguments?.getString("meetingId")?.toLongOrNull() ?: 0L
        MeetingDetailsScreen(
            meetingId = meetingId,
            onBackPressed = {
                navController.popBackStack()
            },
            onParticipantsClick = {
                navController.navigate(MeetRoute.MeetingParticipants.createRoute(meetingId))
            },
            onCommunityClick = { communityId ->
                navController.navigate(MeetRoute.CommunityDetails.createRoute(communityId))
            },
            onHostClick = { userId ->
                navController.navigate(MeetRoute.UserProfile.createRoute(userId))
            }
        )
    }

    composable(MeetRoute.MeetingParticipants.route) { backStackEntry ->
        val meetingId = backStackEntry.arguments?.getString("meetingId")?.toLongOrNull() ?: 0L
        MeetingParticipantsScreen(
            meetingId = meetingId,
            onBackPressed = {
                navController.popBackStack()
            }
        )
    }
}