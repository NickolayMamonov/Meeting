package dev.whysoezzy.meet.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import dev.whysoezzy.meet.navigation.routes.authNavigation
import dev.whysoezzy.meet.navigation.routes.communitiesNavigation
import dev.whysoezzy.meet.navigation.routes.meetingsNavigation
import dev.whysoezzy.meet.navigation.routes.profileNavigation
import org.koin.androidx.compose.koinViewModel

@Composable
fun MeetNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val authViewModel: AuthCheckViewModel = koinViewModel()
    val isLoggedIn by authViewModel.isLoggedIn.collectAsStateWithLifecycle()

    // Показываем SplashScreen пока проверяем авторизацию
    if (isLoggedIn == null) {
//        SplashScreen()
        return
    }

    val startDestination =
        if (isLoggedIn == true) {
            MeetRoute.Main.route
        } else {
            MeetRoute.Auth.route
        }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        authNavigation(navController)
        meetingsNavigation(navController)
        communitiesNavigation(navController)
        profileNavigation(navController)
    }

    AuthStateNavigationEffect(
        navController = navController,
        isLoggedIn = isLoggedIn,
    )
}

@Composable
internal fun AuthStateNavigationEffect(
    navController: NavHostController,
    isLoggedIn: Boolean?,
) {
    var hasInitializedAuthState by remember { mutableStateOf(false) }

    LaunchedEffect(isLoggedIn) {
        if (!hasInitializedAuthState) {
            hasInitializedAuthState = true
        } else if (isLoggedIn == false) {
            navController.navigate(MeetRoute.Auth.route) {
                popUpTo(MeetRoute.Main.route) {
                    inclusive = true
                }
                launchSingleTop = true
            }
        }
    }
}
