package dev.whysoezzy.meet.navigation.routes

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import dev.whysoezzy.auth.presentation.code.CodeVerificationScreen
import dev.whysoezzy.auth.presentation.name.NameInputScreen
import dev.whysoezzy.auth.presentation.phone.PhoneInputScreen
import dev.whysoezzy.auth.presentation.success.AuthSuccessScreen
import dev.whysoezzy.meet.navigation.MeetRoute

fun NavGraphBuilder.authNavigation(navController: NavController) {
    navigation(
        startDestination = MeetRoute.PhoneInput.route,
        route = MeetRoute.Auth.route
    ) {
        composable(MeetRoute.PhoneInput.route) {
            PhoneInputScreen(
                onPhoneSubmitted = { phoneNumber ->
                    navController.navigate(MeetRoute.CodeVerification.createRoute(phoneNumber))
                },
                onBackPressed = {
                    navController.popBackStack()
                }
            )
        }

        composable(MeetRoute.CodeVerification.route) { backStackEntry ->
            val phoneNumber = backStackEntry.arguments?.getString("phoneNumber") ?: ""
            CodeVerificationScreen(
                phoneNumber = phoneNumber,
                onCodeVerified = {
                    navController.navigate(MeetRoute.NameInput.route)
                },
                onBackPressed = {
                    navController.popBackStack()
                }
            )
        }

        composable(MeetRoute.NameInput.route) {
            NameInputScreen(
                onNameSubmitted = {
                    navController.navigate(MeetRoute.AuthSuccess.route)
                },
                onBackPressed = {
                    navController.popBackStack()
                }
            )
        }

        composable(MeetRoute.AuthSuccess.route) {
            AuthSuccessScreen(
                onContinueClicked = {
                    navController.navigate(MeetRoute.Main.route) {
                        popUpTo(MeetRoute.Auth.route) { inclusive = true }
                    }
                }
            )
        }
    }
}