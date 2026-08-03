package dev.whysoezzy.meet.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthStateNavigationEffectTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun unauthorizedRefresh_routesProtectedDestinationToEmailAuthAndExcludesProtectedBackStack() {
        var isLoggedIn by mutableStateOf(true)
        lateinit var navController: NavHostController

        composeTestRule.setContent {
            navController = rememberNavController()

            NavHost(
                navController = navController,
                startDestination = MeetRoute.Main.route,
            ) {
                composable(MeetRoute.Main.route) {
                    Text("Protected main content")
                }
                composable(MeetRoute.MeetingDetails.route) {
                    Text("Protected meeting content")
                }
                navigation(
                    startDestination = MeetRoute.EmailInput.route,
                    route = MeetRoute.Auth.route,
                ) {
                    composable(MeetRoute.EmailInput.route) {
                        Text("Email authentication")
                    }
                }
            }

            AuthStateNavigationEffect(
                navController = navController,
                isLoggedIn = isLoggedIn,
            )
        }

        composeTestRule.runOnIdle {
            navController.navigate(MeetRoute.MeetingDetails.createRoute(1L))
        }
        composeTestRule.onNodeWithText("Protected meeting content").assertIsDisplayed()

        composeTestRule.runOnIdle {
            // The protected request has received a 401/403 refresh failure and cleared the
            // token state, which is exposed to navigation as a logged-out session.
            isLoggedIn = false
        }

        composeTestRule.onNodeWithText("Email authentication").assertIsDisplayed()

        composeTestRule.runOnIdle {
            val protectedRoutes =
                setOf(
                    MeetRoute.Main.route,
                    MeetRoute.MeetingDetails.route,
                )

            assertFalse(navController.currentDestination?.route in protectedRoutes)
            assertTrue(navController.previousBackStackEntry?.destination?.route !in protectedRoutes)

            navController.popBackStack()

            assertFalse(navController.currentDestination?.route in protectedRoutes)
        }
    }
}
