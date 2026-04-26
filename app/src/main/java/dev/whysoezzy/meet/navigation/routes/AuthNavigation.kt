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

        // 1. Ввод телефона
        composable(MeetRoute.PhoneInput.route) {
            PhoneInputScreen(
                onPhoneSubmitted = { phoneNumber ->
                    navController.navigate(
                        MeetRoute.CodeVerification.createRoute(phoneNumber)
                    )
                },
                onBackPressed = { navController.popBackStack() }
            )
        }

        // 2. Ввод кода подтверждения
        composable(MeetRoute.CodeVerification.route) { backStackEntry ->
            val phoneEncoded = backStackEntry.arguments?.getString("phoneNumber") ?: ""
            val phone = MeetRoute.decode(phoneEncoded)

            CodeVerificationScreen(
                phoneNumber = phone,
                onCodeVerifiedExisting = {
                    // Существующий пользователь — сразу в Main, очищаем Auth stack
                    navController.navigate(MeetRoute.Main.route) {
                        popUpTo(MeetRoute.Auth.route) { inclusive = true }
                    }
                },
                onCodeVerifiedNew = { p, code ->
                    // Новый пользователь — нужно ввести имя
                    navController.navigate(MeetRoute.NameInput.createRoute(p, code))
                },
                onBackPressed = { navController.popBackStack() }
            )
        }

        // 3. Ввод имени (только для новых пользователей)
        composable(MeetRoute.NameInput.route) {
            NameInputScreen(
                onNameSubmitted = {
                    navController.navigate(MeetRoute.AuthSuccess.route)
                },
                onBackPressed = { navController.popBackStack() }
            )
        }

        // 4. Экран успешной регистрации
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
