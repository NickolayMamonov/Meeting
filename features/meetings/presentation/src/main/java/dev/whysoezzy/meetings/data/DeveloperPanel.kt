package dev.whysoezzy.meetings.data

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.whysoezzy.uikit.components.buttons.UIKitButton
import dev.whysoezzy.uikit.components.buttons.UIKitButtonState
import dev.whysoezzy.uikit.components.text.TextBody2
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.SpacingTokens

/**
 * Панель разработчика для быстрого переключения между состояниями данных
 * Удобно для тестирования различных сценариев
 */
@Composable
fun DeveloperPanel(
    onScenarioSelected: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = SpacingTokens.XS)
    ) {
        Column(
            modifier = Modifier.padding(SpacingTokens.M),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.S)
        ) {
            TextBody2(text = "Быстрое переключение между состояниями данных")
            
            // Основные сценарии
            Row(
                horizontalArrangement = Arrangement.spacedBy(SpacingTokens.S)
            ) {
                UIKitButton(
                    text = "Demo",
                    state = UIKitButtonState.PRIMARY,
                    onClick = { 
                        QuickScenarios.demo()
                        onScenarioSelected("Demo data enabled")
                    },
                    modifier = Modifier.weight(1f)
                )
                
                UIKitButton(
                    text = "Production",
                    state = UIKitButtonState.SECONDARY,
                    onClick = { 
                        QuickScenarios.production()
                        onScenarioSelected("Production API enabled")
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            

            
            // Производительность
            UIKitButton(
                text = "Slow Loading (Performance Test)",
                state = UIKitButtonState.SECONDARY,
                onClick = { 
                    QuickScenarios.performanceTesting()
                    onScenarioSelected("Slow loading enabled")
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Статистика mock данных
 */
@Composable
fun MockDataStats(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(SpacingTokens.M),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.S)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextBody2(text = "Hero Events:")
                TextBody2(text = "${MockData.heroEvents.size}")
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextBody2(text = "Popular Events:")
                TextBody2(text = "${MockData.popularEvents.size}")
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextBody2(text = "All Events:")
                TextBody2(text = "${MockData.allEvents.size}")
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextBody2(text = "Communities:")
                TextBody2(text = "${MockData.recommendedCommunities.size}")
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextBody2(text = "Ad Blocks:")
                TextBody2(text = "${MockData.adBlocks.size}")
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextBody2(text = "Tags:")
                TextBody2(text = "${MockData.mockTags.size}")
            }
        }
    }
}

/**
 * Индикатор текущего состояния данных
 */
@Composable
fun DataSourceIndicator(
    modifier: Modifier = Modifier
) {
    val config = DataSourceConfig
    val statusText = when {
        !config.useMockData -> "🌐 Production API"
        config.useMockError -> "❌ Error Testing"
        config.useMockEmpty -> "📭 Empty State Testing"
        config.useMockSlow -> "🐌 Performance Testing"
        else -> "🎭 Mock Data"
    }
    
    val statusColor = when {
        !config.useMockData -> MaterialTheme.colorScheme.primary
        config.useMockError -> MaterialTheme.colorScheme.error
        config.useMockEmpty -> MaterialTheme.colorScheme.outline
        config.useMockSlow -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.tertiary
    }
    
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = statusColor.copy(alpha = 0.1f)
        )
    ) {
        TextBody2(
            text = statusText,
            modifier = Modifier.padding(SpacingTokens.S),
            color = statusColor
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DeveloperPanelPreview() {
    UIKitTheme {
        Column(
            modifier = Modifier.padding(SpacingTokens.M),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
        ) {
            DataSourceIndicator()
            DeveloperPanel()
            MockDataStats()
        }
    }
}