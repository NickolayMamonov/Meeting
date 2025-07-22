package dev.whysoezzy.meet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.whysoezzy.domain.models.AdBlock
import dev.whysoezzy.meetings.MainScreen
import dev.whysoezzy.uikit.theme.UIKitTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UIKitTheme {
                MainActivityContent()
            }
        }
    }
}

@Composable
fun MainActivityContent() {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Индикатор текущего состояния данных
//            DataSourceIndicator(
//                modifier = Modifier.padding(
//                    horizontal = SpacingTokens.M,
//                    vertical = SpacingTokens.S
//                )
//            )

//            // Панель разработчика (DEBUG только)
//            if (BuildConfig.DEBUG) {
//                DeveloperPanel(
//                    onScenarioSelected = { message ->
//                        coroutineScope.launch {
//                            snackbarHostState.showSnackbar(
//                                message = message,
//                                withDismissAction = true
//                            )
//                        }
//                    },
//                    modifier = Modifier.padding(
//                        horizontal = SpacingTokens.M,
//                        vertical = SpacingTokens.S
//                    )
//                )
//            }

            // Основной экран
            Surface(
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.background
            ) {
                MainScreen(
                    onEventClick = { event ->
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                message = "Событие: ${event.title}",
                                withDismissAction = true
                            )
                        }
                    },
                    onCommunityClick = { community ->
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                message = "Сообщество: ${community.title}",
                                withDismissAction = true
                            )
                        }
                    },
                    onAdClick = { adBlock ->
                        val message = when (adBlock) {
                            is AdBlock.CommunityAd -> "Реклама сообществ: ${adBlock.title}"
                            is AdBlock.TextAd -> "Текстовая реклама: ${adBlock.title}"
                            is AdBlock.BannerAd -> "Баннер: ${adBlock.title}"
                        }
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                message = message,
                                withDismissAction = true
                            )
                        }
                    }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainActivityPreview() {
    UIKitTheme {
        MainActivityContent()
    }
}