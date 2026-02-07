package dev.whysoezzy.uikit.components.blocks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.whysoezzy.uikit.components.layouts.UIKitOverlappingAvatars
import dev.whysoezzy.uikit.components.text.TextHeading2
import dev.whysoezzy.uikit.components.text.TextMetadata2
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.SpacingTokens

/**
 * Блок с участниками
 *
 * @param title Заголовок блока (по умолчанию "Участники")
 * @param participantAvatars Список URL аватаров участников
 * @param participantCount Общее количество участников
 * @param avatarSize Размер аватаров в dp
 * @param maxVisibleAvatars Максимальное количество видимых аватаров
 * @param onParticipantsClick Колбэк при клике на блок участников
 * @param modifier Модификатор для кастомизации
 */
@Composable
fun UIKitParticipantsBlock(
    modifier: Modifier = Modifier,
    title: String = "Пойдут на встречу",
    participantAvatars: List<String>,
    participantCount: Int,
    avatarSize: Int = 40,
    maxVisibleAvatars: Int = 8,
    onParticipantsClick: () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
    ) {
        // Заголовок с количеством участников
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextHeading2(text = title)
            TextMetadata2(
                text = "$participantCount ${getParticipantCountText(participantCount)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Аватары участников
        UIKitOverlappingAvatars(
            avatarUrls = participantAvatars,
            avatarSize = avatarSize.dp,
            maxVisibleAvatars = maxVisibleAvatars,
            modifier = Modifier.clickable { onParticipantsClick() }
        )
    }
}

/**
 * Возвращает правильную форму слова "участник" в зависимости от количества
 */
private fun getParticipantCountText(count: Int): String {
    return when {
        count % 10 == 1 && count % 100 != 11 -> "участник"
        count % 10 in 2..4 && count % 100 !in 12..14 -> "участника"
        else -> "участников"
    }
}

@Preview
@Composable
private fun UIKitParticipantsBlockPreview() {
    UIKitTheme {
        UIKitParticipantsBlock(
            participantAvatars = listOf(
                "https://picsum.photos/100/100?random=1",
                "https://picsum.photos/100/100?random=2",
                "https://picsum.photos/100/100?random=3",
                "https://picsum.photos/100/100?random=4",
                "https://picsum.photos/100/100?random=5"
            ),
            participantCount = 15,
            onParticipantsClick = { }
        )
    }
}

@Preview
@Composable
private fun UIKitParticipantsBlockSingleParticipantPreview() {
    UIKitTheme {
        UIKitParticipantsBlock(
            participantAvatars = listOf("https://picsum.photos/300/180?random=1"),
            participantCount = 1,
            onParticipantsClick = { }
        )
    }
}

@Preview
@Composable
private fun UIKitParticipantsBlockManyParticipantsPreview() {
    UIKitTheme {
        UIKitParticipantsBlock(
            title = "Записались",
            participantAvatars = (1..15).map { "https://picsum.photos/100/100?random=$it" },
            participantCount = 42,
            avatarSize = 48,
            maxVisibleAvatars = 10,
            onParticipantsClick = { }
        )
    }
}
