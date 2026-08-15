package dev.whysoezzy.meet

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.rememberNavController
import com.whysoezzy.common.push.MeetingJoinEvents
import dev.whysoezzy.meet.navigation.MeetNavHost
import dev.whysoezzy.meet.navigation.MeetRoute
import dev.whysoezzy.meet.push.NotificationPermissionPolicy
import dev.whysoezzy.meet.push.PushRegistrationCoordinator
import dev.whysoezzy.meet.push.PushStateStore
import dev.whysoezzy.meet.push.PushTapCommand
import dev.whysoezzy.meet.push.PushTapIntent
import dev.whysoezzy.uikit.adaptive.LocalWindowSizeClass
import dev.whysoezzy.uikit.theme.UIKitTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val pushStateStore: PushStateStore by inject()
    private val pushRegistrationCoordinator: PushRegistrationCoordinator by inject()
    private val pushTapCommands = MutableSharedFlow<PushTapCommand>(
        replay = 1,
        extraBufferCapacity = 1,
    )
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            lifecycleScope.launch { pushRegistrationCoordinator.drainPendingDisplays() }
        }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        observeSuccessfulJoins()
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            CompositionLocalProvider(LocalWindowSizeClass provides windowSizeClass) {
                UIKitTheme {
                    MeetApp(pushTapCommands, pushRegistrationCoordinator)
                }
            }
        }
        PushTapIntent.read(intent)?.let(pushTapCommands::tryEmit)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        PushTapIntent.read(intent)?.let(pushTapCommands::tryEmit)
    }

    private fun observeSuccessfulJoins() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                MeetingJoinEvents.events.collect {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || it == null) return@collect
                    val eligible = pushStateStore.update(NotificationPermissionPolicy::markSuccessfulJoin)
                    if (!NotificationPermissionPolicy.shouldRequest(eligible, Build.VERSION.SDK_INT)) {
                        return@collect
                    }
                    pushStateStore.update(NotificationPermissionPolicy::markRequested)
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { pushRegistrationCoordinator.drainPendingDisplays() }
    }
}

@Composable
internal fun MeetApp(
    pushTapCommands: SharedFlow<PushTapCommand>? = null,
    coordinator: PushRegistrationCoordinator? = null,
) {
    val navController = rememberNavController()
    var navHostReady by remember { mutableStateOf(false) }

    if (pushTapCommands != null && coordinator != null && navHostReady) {
        LaunchedEffect(pushTapCommands, navHostReady) {
            pushTapCommands.collect { command ->
                if (coordinator.claimTap(command)) {
                    val destination = MeetRoute.MeetingDetails.createRoute(command.meetingId)
                    val currentMeetingId = navController.currentBackStackEntry
                        ?.arguments
                        ?.getString("meetingId")
                        ?.toLongOrNull()
                    val alreadyAtDestination =
                        navController.currentDestination?.route == MeetRoute.MeetingDetails.route &&
                            currentMeetingId == command.meetingId
                    if (!alreadyAtDestination) {
                        navController.navigate(destination) {
                            launchSingleTop = true
                        }
                    }
                    coordinator.completeTap(command)
                }
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        MeetNavHost(
            navController = navController,
            onReady = { navHostReady = true },
        )
    }
}
