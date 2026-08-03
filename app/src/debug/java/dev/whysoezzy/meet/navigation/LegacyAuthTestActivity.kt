package dev.whysoezzy.meet.navigation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.navigation.NavGraph
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.DialogNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.navArgument
import androidx.navigation.navigation
import dev.whysoezzy.meet.navigation.routes.authNavigation

class LegacyAuthTestActivity : ComponentActivity() {
    lateinit var navController: NavHostController
        private set
    var restoredLegacyDestinationId: Int? = null
        private set
    var legacyRedirectRequestedAfterRestoration: Boolean = false
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val useUpgradedGraph = useUpgradedGraphForNextCreate
        val legacyStacks = readLegacyStackSpecs()
        val restoredLegacyRoute = savedLegacyRouteForNextCreate
        savedLegacyRouteForNextCreate = null
        navController = NavHostController(this).also { controller ->
            controller.navigatorProvider.addNavigator(ComposeNavigator())
            controller.navigatorProvider.addNavigator(DialogNavigator())
        }
        navController.setLifecycleOwner(this)
        navController.setViewModelStore(viewModelStore)
        navController.setOnBackPressedDispatcher(onBackPressedDispatcher)
        val graph = buildGraph(navController, useUpgradedGraph, legacyStacks, restoredLegacyRoute)
        navController.graph = graph
        setContent { NavHost(navController = navController, graph = graph) }
    }

    private fun buildGraph(
        controller: NavHostController,
        useUpgradedGraph: Boolean,
        legacyStacks: List<LegacyStackSpec>,
        restoredLegacyRoute: String?,
    ): NavGraph = controller.createGraph(startDestination = MeetRoute.Auth.route) {
        if (useUpgradedGraph) {
            authNavigation(
                navController = controller,
                startDestination = restoredLegacyRoute ?: MeetRoute.EmailInput.route,
                onLegacyDestinationComposed = { restoredLegacyDestinationId = it },
                onLegacyRedirectRequested = {
                    check(restoredLegacyDestinationId != null) {
                        "Legacy redirect started before restored destination was composed"
                    }
                    legacyRedirectRequestedAfterRestoration = true
                    redirectLegacyAuth(controller)
                },
            )
        } else {
            navigation(
                startDestination = legacyStacks.first().route,
                route = MeetRoute.Auth.route,
            ) {
                legacyStacks.forEach { fixture ->
                    composable(
                        route = fixture.route,
                        arguments = fixture.argumentNames.map { argumentName ->
                            navArgument(argumentName) { type = NavType.StringType }
                        },
                    ) {
                        Text("legacy")
                    }
                }
            }
        }
    }

    private fun readLegacyStackSpecs(): List<LegacyStackSpec> =
        intent
            .getStringArrayListExtra(EXTRA_LEGACY_STACKS)
            .orEmpty()
            .map { serialized ->
                val parts = serialized.split('|')
                LegacyStackSpec(parts.first(), parts.drop(1))
            }

    private data class LegacyStackSpec(
        val route: String,
        val argumentNames: List<String>,
    )

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBundle(NAV_STATE, navController.saveState())
        super.onSaveInstanceState(outState)
    }

    companion object {
        private const val NAV_STATE = "legacy-auth-nav-state"
        var savedLegacyRouteForNextCreate: String? = null
        const val EXTRA_LEGACY_STACKS = "legacyStacks"

        @Volatile
        var useUpgradedGraphForNextCreate: Boolean = false
    }
}
