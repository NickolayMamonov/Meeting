package dev.whysoezzy.meet.navigation.routes

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.whysoezzy.meet.navigation.MeetRoute
import dev.whysoezzy.meetings.MainScreen
import dev.whysoezzy.meetings.details.presentation.MeetingDetailsScreen
import dev.whysoezzy.meetings.participants.presentation.MeetingParticipantsScreen

fun NavGraphBuilder.meetingsNavigation(navController: NavController) {
    composable(MeetRoute.Main.route) {
        MainScreen(
            onMeetingClick = { meetingId ->
                navController.navigate(MeetRoute.MeetingDetails.createRoute(meetingId))
            },
            onCommunityClick = { communityId ->
                navController.navigate(MeetRoute.CommunityDetails.createRoute(communityId))
            },
            onProfileClick = {
                navController.navigate(MeetRoute.Profile.route)
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