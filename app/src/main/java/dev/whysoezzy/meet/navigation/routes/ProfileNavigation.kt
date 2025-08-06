package dev.whysoezzy.meet.navigation.routes

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.whysoezzy.meet.navigation.MeetRoute
import dev.whysoezzy.profile.details.presentation.ProfileDetailsScreen
import dev.whysoezzy.profile.edit.presentation.ProfileEditScreen

fun NavGraphBuilder.profileNavigation(navController: NavController) {
    composable(MeetRoute.Profile.route) {
        ProfileDetailsScreen(
            onBackPressed = {
                navController.popBackStack()
            },
            onEditClick = {
                navController.navigate(MeetRoute.ProfileEdit.route)
            }
        )
    }

    composable(MeetRoute.ProfileEdit.route) {
        ProfileEditScreen(
            onBackPressed = {
                navController.popBackStack()
            },
            onSaveSuccess = {
                navController.popBackStack()
            }
        )
    }
}