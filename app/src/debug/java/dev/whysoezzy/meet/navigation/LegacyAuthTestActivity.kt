package dev.whysoezzy.meet.navigation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navigation

class LegacyAuthTestActivity : ComponentActivity() {
    lateinit var navController: NavHostController
        private set
    var restoredLegacyDestinationId: Int? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val useUpgradedGraph = useUpgradedGraphForNextCreate
        val legacyStacks = readLegacyStackSpecs()
        setContent {
            val controller = rememberNavController()
            navController = controller
            LegacyAuthTestContent(controller, useUpgradedGraph, legacyStacks)
        }
    }

    @Composable
    private fun LegacyAuthTestContent(
        controller: NavHostController,
        useUpgradedGraph: Boolean,
        legacyStacks: List<LegacyStackSpec>,
    ) {
        NavHost(
            navController = controller,
            startDestination = MeetRoute.Auth.route,
        ) {
            navigation(
                startDestination =
                    if (useUpgradedGraph) {
                        MeetRoute.EmailInput.route
                    } else {
                        legacyStacks.first().route
                    },
                route = MeetRoute.Auth.route,
            ) {
                if (useUpgradedGraph) {
                    composable(MeetRoute.EmailInput.route) {
                        Text("email")
                    }
                    composable(
                        route = MeetRoute.CodeVerification.route,
                        arguments = listOf(
                            navArgument("attemptId") { type = NavType.StringType },
                        ),
                    ) {
                        Text("code")
                    }
                    registerLegacyAuthCompatibilityDestinations(this, controller) {
                        restoredLegacyDestinationId = it
                    }
                } else {
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

    companion object {
        const val EXTRA_LEGACY_STACKS = "legacyStacks"

        @Volatile
        var useUpgradedGraphForNextCreate: Boolean = false
    }
}
