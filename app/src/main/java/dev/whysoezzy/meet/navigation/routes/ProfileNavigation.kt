package dev.whysoezzy.meet.navigation.routes

import android.util.Log
import androidx.compose.runtime.remember
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.whysoezzy.auth.domain.usecase.IsLoggedInUseCase
import dev.whysoezzy.meet.navigation.MeetRoute
import dev.whysoezzy.profile.details.presentation.ProfileDetailsScreen
import dev.whysoezzy.profile.edit.presentation.ProfileEditScreen
import org.koin.compose.koinInject

private const val TAG = "ProfileNavigation"

fun NavGraphBuilder.profileNavigation(navController: NavController) {

    // Собственный профиль — требует авторизации
    composable(MeetRoute.Profile.route) {
        val isLoggedInUseCase: IsLoggedInUseCase = koinInject()
        val isLoggedIn = remember { isLoggedInUseCase() }

        if (!isLoggedIn) {
            // Незалогинен — отправляем на авторизацию
            androidx.compose.runtime.LaunchedEffect(Unit) {
                navController.navigate(MeetRoute.Auth.route) {
                    popUpTo(MeetRoute.Profile.route) { inclusive = true }
                }
            }
        } else {
            ProfileDetailsScreen(
                userId = null,
                onBackPressed = { navController.popBackStack() },
                onEditClick = { navController.navigate(MeetRoute.ProfileEdit.route) },
                onLogout = {
                    navController.navigate(MeetRoute.Main.route) {
                        popUpTo(MeetRoute.Main.route) { inclusive = false }
                    }
                },
                onMeetingClick = { meetingId ->
                    navController.navigate(MeetRoute.MeetingDetails.createRoute(meetingId))
                },
                onCommunityClick = { communityId ->
                    navController.navigate(MeetRoute.CommunityDetails.createRoute(communityId))
                }
            )
        }
    }

    // Профиль другого пользователя
    composable(
        route = MeetRoute.UserProfile.route,
        arguments = listOf(navArgument("userId") { type = NavType.LongType })
    ) { backStackEntry ->
        val userId = backStackEntry.arguments?.getLong("userId") ?: 0L
        Log.d(TAG, "UserProfile composable: userId=$userId")
        ProfileDetailsScreen(
            userId = userId,
            onBackPressed = { navController.popBackStack() },
            onMeetingClick = { meetingId ->
                navController.navigate(MeetRoute.MeetingDetails.createRoute(meetingId))
            },
            onCommunityClick = { communityId ->
                navController.navigate(MeetRoute.CommunityDetails.createRoute(communityId))
            }
        )
    }

    // Редактирование профиля
    composable(MeetRoute.ProfileEdit.route) {
        ProfileEditScreen(
            onBackPressed = { navController.popBackStack() },
            onSaveSuccess = { navController.popBackStack() }
        )
    }
}
