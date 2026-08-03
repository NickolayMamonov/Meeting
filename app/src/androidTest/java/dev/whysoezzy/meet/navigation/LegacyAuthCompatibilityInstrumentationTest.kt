package dev.whysoezzy.meet.navigation

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import androidx.navigation.NavGraph
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.createGraph
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.whysoezzy.meet.navigation.routes.authNavigation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegacyAuthCompatibilityInstrumentationTest {
    @Test
    fun upgradedGraph_registersIndependentLegacyIdContract() {
        onMain {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val controller = testController(context)
            val graph = upgradedGraph(controller)
            val authGraph =
                requireNotNull(
                    graph.findNode(LegacyAuthTestFixture.authGraphId) as? NavGraph,
                )

            LegacyAuthCompatibility.assertIds(graph)

            LegacyAuthTestFixture.stacks.forEach { fixture ->
                val destination = authGraph.findNode(fixture.destinationId)
                assertNotNull(destination)
                assertEquals(fixture.route, destination?.route)
                assertEquals(fixture.destinationId, destination?.id)
            }
            val activeCode = requireNotNull(
                authGraph.findNode(MeetRoute.CodeVerification.destinationId),
            )
            assertEquals(MeetRoute.CodeVerification.route, activeCode.route)
            assertEquals(MeetRoute.CodeVerification.destinationId, activeCode.id)
            assertTrue(
                LegacyAuthTestFixture.stacks
                    .map(LegacyStackFixture::destinationId)
                    .none { it == activeCode.id },
            )
        }
    }

    @Test
    fun eachLegacyStack_survivesActivityRecreationAndRedirectsWithoutLegacyArguments() {
        LegacyAuthTestFixture.stacks.forEach { fixture ->
            LegacyAuthTestActivity.useUpgradedGraphForNextCreate = false
            val scenario = ActivityScenario.launch<LegacyAuthTestActivity>(
                Intent(
                    InstrumentationRegistry.getInstrumentation().targetContext,
                    LegacyAuthTestActivity::class.java,
                ).apply {
                    putStringArrayListExtra(
                        LegacyAuthTestActivity.EXTRA_LEGACY_STACKS,
                        ArrayList(
                            LegacyAuthTestFixture.stacks.map { stack ->
                                listOf(stack.route, *stack.argumentNames.toTypedArray())
                                    .joinToString("|")
                            },
                        ),
                    )
                },
            )
            try {
                waitForIdle()
                scenario.onActivity { activity ->
                    fixture.navigationPath.forEach(activity.navController::navigate)
                    assertEquals(fixture.route, activity.navController.currentDestination?.route)
                    assertEquals(fixture.destinationId, activity.navController.currentDestination?.id)
                    assertEquals(
                        fixture.argumentNames,
                        currentArgumentNames(activity.navController) - DEEP_LINK_INTENT_KEY,
                    )
                    LegacyAuthTestActivity.savedLegacyRouteForNextCreate = fixture.route
                    LegacyAuthTestActivity.useUpgradedGraphForNextCreate = true
                }

                scenario.recreate()
                waitForIdle()
                scenario.onActivity { activity ->
                    assertEquals(fixture.destinationId, activity.restoredLegacyDestinationId)
                    assertTrue(activity.legacyRedirectRequestedAfterRestoration)
                    assertArgumentFreeEmailTarget(activity)
                }

                scenario.recreate()
                waitForIdle()
                scenario.onActivity { activity -> assertArgumentFreeEmailTarget(activity) }
            } finally {
                scenario.close()
                LegacyAuthTestActivity.useUpgradedGraphForNextCreate = false
                LegacyAuthTestActivity.savedLegacyRouteForNextCreate = null
            }
        }
    }

    @SuppressLint("RestrictedApi")
    private fun assertArgumentFreeEmailTarget(activity: LegacyAuthTestActivity) {
        val controller = activity.navController
        assertEquals(MeetRoute.EmailInput.route, controller.currentDestination?.route)
        val rawArgumentNames = currentArgumentNames(controller)
        assertTrue(rawArgumentNames.none { it == "phone" })
        assertTrue(rawArgumentNames.none { it == "phoneNumber" })
        assertTrue(rawArgumentNames.none { it == "code" })
        val legacyIds =
            LegacyAuthTestFixture.stacks.map(LegacyStackFixture::destinationId).toSet()
        assertTrue(controller.currentBackStack.value.none { it.destination.id in legacyIds })
    }

    private fun onMain(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    }

    private fun waitForIdle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun currentArgumentNames(controller: NavHostController): Set<String> =
        controller
            .currentBackStackEntry
            ?.arguments
            ?.keySet()
            ?.toSet()
            .orEmpty()

    private fun testController(context: Context): TestNavHostController =
        TestNavHostController(context).also {
            it.navigatorProvider.addNavigator(ComposeNavigator())
        }

    private fun upgradedGraph(controller: NavHostController) =
        controller.createGraph(
            startDestination = MeetRoute.Auth.route,
            route = ROOT_GRAPH_ROUTE,
        ) {
            authNavigation(controller)
        }

    private companion object {
        const val DEEP_LINK_INTENT_KEY = "android-support-nav:controller:deepLinkIntent"
        const val ROOT_GRAPH_ROUTE = "test/root"
    }
}
