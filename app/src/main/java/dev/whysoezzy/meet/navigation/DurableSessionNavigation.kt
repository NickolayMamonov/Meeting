package dev.whysoezzy.meet.navigation

import androidx.navigation.NavController
import com.whysoezzy.auth.domain.models.AuthSession

internal fun NavController.resolveFromDurableSession(session: AuthSession) {
    val currentRoute = currentDestination?.route.orEmpty()
    val target = when (session.stage) {
        AuthSession.Stage.LoggedOut -> MeetRoute.EmailInput.route
        AuthSession.Stage.NeedsName -> MeetRoute.NameInput.route
        AuthSession.Stage.Welcome -> MeetRoute.AuthSuccess.route
        AuthSession.Stage.Ready ->
            if (currentRoute.startsWith("profile")) MeetRoute.Profile.route else MeetRoute.Main.route
    }

    if (currentRoute == target) return
    navigate(target) {
        popUpTo(graph.id)
        launchSingleTop = true
    }
}
