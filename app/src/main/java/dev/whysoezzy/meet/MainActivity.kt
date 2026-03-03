package dev.whysoezzy.meet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import dev.whysoezzy.meet.navigation.MeetNavHost
import dev.whysoezzy.uikit.theme.UIKitTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UIKitTheme {
                MeetApp()
            }
        }
    }
}

@Composable
fun MeetApp() {
    val navController = rememberNavController()

    // Без Scaffold на верхнем уровне — каждый экран управляет своими WindowInsets сам.
    // ProfileDetailsScreen / ProfileEditScreen: фото edge-to-edge + TopBar с statusBarsPadding.
    // MainScreen и остальные: используют внутренний Scaffold, который сам добавляет нужные отступы.
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        MeetNavHost(navController = navController)
    }
}
