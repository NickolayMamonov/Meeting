package dev.whysoezzy.meet.navigation.routes

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import dev.whysoezzy.auth.presentation.name.NameInputMode
import dev.whysoezzy.auth.presentation.name.NameInputScreen
import dev.whysoezzy.meet.navigation.MeetRoute
import dev.whysoezzy.meet.navigation.resolveFromDurableSession
import dev.whysoezzy.profile.details.presentation.ProfileDetailsScreen
import dev.whysoezzy.profile.details.presentation.ProfileMode
import dev.whysoezzy.profile.edit.presentation.ProfileEditScreen

fun NavGraphBuilder.profileNavigation(navController: NavController) {
    // Root auth-state navigation owns logout/forced-logout transitions. Profile only renders
    // its content while this destination is active, avoiding competing navigation owners.
    composable(MeetRoute.Profile.route) {
        ProfileDetailsScreen(
            mode = ProfileMode.Self,
            onBackPressed = { navController.popBackStack() },
            onEditClick = { navController.navigate(MeetRoute.ProfileEdit.route) },
            onNameInput = {
                navController.navigate(MeetRoute.NameInputFromProfile.route)
            },
            onMeetingClick = { meetingId ->
                navController.navigate(MeetRoute.MeetingDetails.createRoute(meetingId))
            },
            onCommunityClick = { communityId ->
                navController.navigate(MeetRoute.CommunityDetails.createRoute(communityId))
            },
        )
    }

    // Профиль другого пользователя
    composable(
        route = MeetRoute.UserProfile.route,
        arguments = listOf(navArgument("userId") { type = NavType.LongType }),
    ) { backStackEntry ->
        val userId = backStackEntry.arguments?.getLong("userId") ?: 0L
        ProfileDetailsScreen(
            mode = ProfileMode.Other(userId),
            onBackPressed = { navController.popBackStack() },
            onMeetingClick = { meetingId ->
                navController.navigate(MeetRoute.MeetingDetails.createRoute(meetingId))
            },
            onCommunityClick = { communityId ->
                navController.navigate(MeetRoute.CommunityDetails.createRoute(communityId))
            },
        )
    }

    // Редактирование профиля
    composable(MeetRoute.ProfileEdit.route) {
        ProfileEditScreen(
            onBackPressed = { navController.popBackStack() },
            onSaveSuccess = { navController.popBackStack() },
        )
    }

    // Ввод имени для пользователей с пустым профилем (существующих без имени)
    composable(MeetRoute.NameInputFromProfile.route) {
        NameInputScreen(
            mode = NameInputMode.ProfileCompletion,
            onNameSubmitted = {},
            onProfileCompleted = {
                // После ввода имени — возвращаемся на профиль (он теперь перезагрузится с именем)
                navController.navigate(MeetRoute.Profile.route) {
                    popUpTo(MeetRoute.NameInputFromProfile.route) { inclusive = true }
                }
            },
            onResolveFromDurableSession = navController::resolveFromDurableSession,
            onBackPressed = { navController.popBackStack() },
        )
    }
}
