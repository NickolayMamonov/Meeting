package dev.whysoezzy.meet.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navigation
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.whysoezzy.auth.domain.models.AuthSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NameInputModeNavigationInstrumentationTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun onboardingWelcome_resolvesToAuthSuccess_withoutLeavingProtectedStack() {
        lateinit var navController: NavHostController

        composeTestRule.setContent {
            navController = rememberNavController()
            testGraph(navController)
        }

        composeTestRule.runOnIdle {
            navController.navigate(MeetRoute.NameInput.route)
            navController.resolveFromDurableSession(
                AuthSession(1L, AuthSession.Stage.Welcome),
            )
            assertEquals(MeetRoute.AuthSuccess.route, navController.currentDestination?.route)
            assertTrue(navController.previousBackStackEntry == null)
        }
    }

    @Test
    fun profileCompletionReady_resolvesToProfile_andNeverAuthSuccess() {
        lateinit var navController: NavHostController

        composeTestRule.setContent {
            navController = rememberNavController()
            testGraph(navController)
        }

        composeTestRule.runOnIdle {
            navController.navigate(MeetRoute.NameInputFromProfile.route)
            navController.resolveFromDurableSession(
                AuthSession(1L, AuthSession.Stage.Ready),
            )
            assertEquals(MeetRoute.Profile.route, navController.currentDestination?.route)
            assertTrue(
                navController.currentDestination?.route != MeetRoute.AuthSuccess.route,
            )
            assertTrue(navController.previousBackStackEntry == null)
        }
    }

    @Test
    fun allDurableStages_resolveToTheMatchingRootDestination() {
        lateinit var navController: NavHostController

        composeTestRule.setContent {
            navController = rememberNavController()
            testGraph(navController)
        }

        composeTestRule.runOnIdle {
            navController.resolveFromDurableSession(AuthSession.LoggedOut)
            assertEquals(MeetRoute.EmailInput.route, navController.currentDestination?.route)

            navController.resolveFromDurableSession(
                AuthSession(1L, AuthSession.Stage.NeedsName),
            )
            assertEquals(MeetRoute.NameInput.route, navController.currentDestination?.route)

            navController.resolveFromDurableSession(
                AuthSession(1L, AuthSession.Stage.Welcome),
            )
            assertEquals(MeetRoute.AuthSuccess.route, navController.currentDestination?.route)

            navController.resolveFromDurableSession(
                AuthSession(1L, AuthSession.Stage.Ready),
            )
            assertEquals(MeetRoute.Main.route, navController.currentDestination?.route)
            assertTrue(navController.previousBackStackEntry == null)
        }
    }

    @Composable
    private fun testGraph(navController: NavHostController) {
        NavHost(
            navController = navController,
            startDestination = MeetRoute.Auth.route,
        ) {
            navigation(
                route = MeetRoute.Auth.route,
                startDestination = MeetRoute.EmailInput.route,
            ) {
                composable(MeetRoute.EmailInput.route) { Text("email") }
                composable(MeetRoute.NameInput.route) { Text("name") }
                composable(MeetRoute.AuthSuccess.route) { Text("success") }
            }
            composable(MeetRoute.Main.route) { Text("main") }
            composable(MeetRoute.Profile.route) { Text("profile") }
            composable(MeetRoute.NameInputFromProfile.route) { Text("profile-name") }
        }
    }
}
