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
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.rememberNavController
import com.whysoezzy.common.push.MeetingJoinEvents
import dev.whysoezzy.meet.navigation.MeetNavHost
import dev.whysoezzy.meet.navigation.MeetRoute
import dev.whysoezzy.meet.push.PermissionState
import dev.whysoezzy.meet.push.PushRegistrationCoordinator
import dev.whysoezzy.meet.push.PushStateReducer
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
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

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
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@collect
                    val eligible = pushStateStore.update(PushStateReducer::markPermissionEligible)
                    if (eligible.installPolicy.permission != PermissionState.ELIGIBLE) return@collect
                    pushStateStore.update(PushStateReducer::markPermissionRequested)
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }
}

@Composable
internal fun MeetApp(
    pushTapCommands: SharedFlow<PushTapCommand>? = null,
    coordinator: PushRegistrationCoordinator? = null,
) {
    val navController = rememberNavController()

    if (pushTapCommands != null && coordinator != null) {
        LaunchedEffect(pushTapCommands) {
            pushTapCommands.collect { command ->
                if (coordinator.claimTap(command)) {
                    navController.navigate(MeetRoute.MeetingDetails.createRoute(command.meetingId)) {
                        launchSingleTop = true
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
        MeetNavHost(navController = navController)
    }
}
