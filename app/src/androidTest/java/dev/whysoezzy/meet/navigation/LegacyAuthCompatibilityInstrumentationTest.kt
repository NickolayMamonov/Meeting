package dev.whysoezzy.meet.navigation

import android.content.Context
import androidx.navigation.NavGraph
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.whysoezzy.meet.navigation.routes.authNavigation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegacyAuthCompatibilityInstrumentationTest {
    @Test
    fun upgradedGraph_registersExactLegacyIdsAndSeparatesActiveCodeId() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val controller = testController(context)
        val graph = upgradedGraph(controller)
        val authGraph = requireNotNull(graph.findNode(MeetRoute.Auth.route) as? NavGraph)

        LegacyAuthCompatibility.assertIds(graph)

        LegacyAuthCompatibility.routes.forEach { route ->
            val destination = authGraph.findNode(route)
            assertNotNull(destination)
            assertEquals(route, destination?.route)
        }
        val activeCode = requireNotNull(authGraph.findNode(MeetRoute.CodeVerification.route))
        assertEquals(MeetRoute.CodeVerification.destinationId, activeCode.id)
        assertTrue(
            LegacyAuthCompatibility.routes
                .map { requireNotNull(authGraph.findNode(it)).id }
                .none { it == activeCode.id },
        )
    }

    @Test
    fun savedLegacyStack_restoresAgainstUpgradedGraphAndRedirectsWithoutLegacyArguments() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val oldController = testController(context)
        oldController.graph = legacyGraph(oldController)
        oldController.navigate("auth/name/old-phone/old-code")
        val oldDestinationId = requireNotNull(oldController.currentDestination).id
        val savedState = requireNotNull(oldController.saveState())

        val restoredController = testController(context)
        restoredController.restoreState(savedState)
        restoredController.graph = upgradedGraph(restoredController)

        assertEquals(
            "auth/name/{phone}/{code}",
            restoredController.currentDestination?.route,
        )
        assertEquals(oldDestinationId, restoredController.currentDestination?.id)
        assertTrue(
            restoredController.currentBackStackEntry?.arguments?.containsKey("phone") == true,
        )
        assertTrue(
            restoredController.currentBackStackEntry?.arguments?.containsKey("code") == true,
        )

        redirectLegacyAuth(restoredController)

        assertEquals(MeetRoute.EmailInput.route, restoredController.currentDestination?.route)
        assertFalse(
            restoredController.currentBackStackEntry?.arguments?.containsKey("phone") == true,
        )
        assertFalse(
            restoredController.currentBackStackEntry?.arguments?.containsKey("code") == true,
        )
    }

    private fun testController(context: Context): TestNavHostController =
        TestNavHostController(context).also {
            it.navigatorProvider.addNavigator(ComposeNavigator())
        }

    private fun upgradedGraph(controller: NavHostController) =
        controller.createGraph(
            startDestination = MeetRoute.Auth.route,
            route = "test/root",
        ) {
            authNavigation(controller)
        }

    private fun legacyGraph(controller: NavHostController) =
        controller.createGraph(
            startDestination = MeetRoute.Auth.route,
            route = "test/root",
        ) {
            navigation(
                startDestination = LegacyAuthCompatibility.routes.first(),
                route = MeetRoute.Auth.route,
            ) {
                LegacyAuthCompatibility.routes.forEach { route ->
                    composable(route) {}
                }
            }
        }
}
