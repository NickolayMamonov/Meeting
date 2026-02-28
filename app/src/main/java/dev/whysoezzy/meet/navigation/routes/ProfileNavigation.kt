package dev.whysoezzy.meet.navigation.routes

import android.util.Log
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import dev.whysoezzy.meet.navigation.MeetRoute
import dev.whysoezzy.profile.details.presentation.ProfileDetailsScreen
import dev.whysoezzy.profile.edit.presentation.ProfileEditScreen

private const val TAG = "ProfileNavigation"

fun NavGraphBuilder.profileNavigation(navController: NavController) {
    Log.d(TAG, "Registering profile navigation routes")

    composable(MeetRoute.Profile.route) {
        Log.d(TAG, "ProfileDetailsScreen (own profile) composable created")
        ProfileDetailsScreen(
            userId = null,
            onBackPressed = {
                Log.d(TAG, "Profile back pressed")
                navController.popBackStack()
            },
            onEditClick = {
                Log.d(TAG, "Edit profile clicked")
                navController.navigate(MeetRoute.ProfileEdit.route)
            },
            onMeetingClick = { meetingId ->
                Log.d(TAG, "Meeting clicked from profile: $meetingId")
                navController.navigate(MeetRoute.MeetingDetails.createRoute(meetingId))
            },
            onCommunityClick = { communityId ->
                Log.d(TAG, "Community clicked from profile: $communityId")
                navController.navigate(MeetRoute.CommunityDetails.createRoute(communityId))
            }
        )
    }

    composable(
        route = MeetRoute.UserProfile.route,
        arguments = listOf(
            navArgument("userId") { type = NavType.LongType }
        )
    ) { backStackEntry ->
        val userId = backStackEntry.arguments?.getLong("userId")
        Log.d(TAG, "ProfileDetailsScreen (user $userId) composable created")
        ProfileDetailsScreen(
            userId = userId,
            onBackPressed = {
                Log.d(TAG, "User profile back pressed")
                navController.popBackStack()
            },
            onMeetingClick = { meetingId ->
                Log.d(TAG, "Meeting clicked from user profile: $meetingId")
                navController.navigate(MeetRoute.MeetingDetails.createRoute(meetingId))
            },
            onCommunityClick = { communityId ->
                Log.d(TAG, "Community clicked from user profile: $communityId")
                navController.navigate(MeetRoute.CommunityDetails.createRoute(communityId))
            }
        )
    }

    composable(MeetRoute.ProfileEdit.route) {
        Log.d(TAG, "ProfileEditScreen composable created")
        ProfileEditScreen(
            onBackPressed = {
                Log.d(TAG, "Edit profile back pressed")
                navController.popBackStack()
            },
            onSaveSuccess = {
                Log.d(TAG, "Profile saved successfully")
                navController.popBackStack()
            }
        )
    }
}


