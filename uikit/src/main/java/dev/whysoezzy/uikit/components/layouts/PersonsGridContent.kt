package dev.whysoezzy.uikit.components.layouts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.whysoezzy.uikit.components.buttons.UIKitButton
import dev.whysoezzy.uikit.components.cards.UIKitPersonCard
import dev.whysoezzy.uikit.tokens.SpacingTokens

/**
 * Переиспользуемый компонент для отображения списка людей (участники, подписчики и т.д.)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonsGridContent(
    persons: List<PersonItem>,
    onPersonClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    emptyStateText: String = "Пока никого нет",
    maxItemsInRow: Int = 3,
) {
    LazyColumn(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = SpacingTokens.L),
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.L),
    ) {
        item {
            if (persons.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalArrangement = Arrangement.spacedBy(SpacingTokens.M),
                    maxItemsInEachRow = maxItemsInRow,
                ) {
                    persons.forEach { person ->
                        UIKitPersonCard(
                            name = person.name,
                            role = person.role,
                            imageUrl = person.imageUrl,
                            onCardClick = { onPersonClick(person.id) },
                        )
                    }
                }
            } else {
                // Empty state
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(SpacingTokens.XL),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = emptyStateText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        item {
            Box(modifier = Modifier.padding(SpacingTokens.L))
        }
    }
}

/**
 * Loading state для списка людей
 */
@Composable
fun PersonsGridLoading(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

/**
 * Error state для списка людей
 */
private val ErrorButtonMaxWidth = 343.dp

@Composable
fun PersonsGridError(
    message: String,
    onRetry: () -> Unit,
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.M),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )

            UIKitButton(
                text = "Повторить",
                onClick = onRetry,
                modifier = Modifier.widthIn(max = ErrorButtonMaxWidth).fillMaxWidth(),
            )

            UIKitButton(
                text = "Назад",
                onClick = onBackPressed,
                modifier = Modifier.widthIn(max = ErrorButtonMaxWidth).fillMaxWidth(),
            )
        }
    }
}

/**
 * Data class для элемента списка людей
 */
data class PersonItem(
    val id: Long,
    val name: String,
    val role: String,
    val imageUrl: String,
)
