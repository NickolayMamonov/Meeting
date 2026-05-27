package dev.whysoezzy.uikit.components.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.whysoezzy.uikit.components.avatars.UIKitAvatar
import dev.whysoezzy.uikit.components.text.TextBody2
import dev.whysoezzy.uikit.components.text.TextHeading2
import dev.whysoezzy.uikit.components.text.TextSubheading1
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.SpacingTokens

@Composable
fun UIKitHostCard(
    name: String,
    surname: String,
    description: String,
    imageUrl: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    onCardClick: (() -> Unit)? = null,
) {
    val colorScheme = UIKitTheme.colors

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.M),
    ) {
        // Optional title
        title?.let {
            TextHeading2(text = it)
        }

        // Host content
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(
                        onCardClick?.let {
                            Modifier.clickable { it() }
                        } ?: Modifier,
                    ),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(SpacingTokens.M),
        ) {
            // Host info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(SpacingTokens.XS),
            ) {
                TextSubheading1(
                    text = "$name $surname",
                )
                TextBody2(
                    text = description,
                    color = colorScheme.neutralWeak,
                )
            }

            // Avatar
            UIKitAvatar(
                imageUrl = imageUrl,
                size = 96.dp,
                clipType = RoundedCornerShape(8.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun UIKitHostCardPreview() {
    UIKitTheme {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(SpacingTokens.L),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.XL),
        ) {
            // Host card with title
            UIKitHostCard(
                title = "Ведущий",
                name = "Александр",
                surname = "Петров",
                description = "Senior Android Developer с 8-летним опытом разработки мобильных приложений",
                imageUrl = "https://picsum.photos/300/180?random=5",
                onCardClick = { /* Handle click */ },
            )

            // Host card without title
            UIKitHostCard(
                name = "Мария",
                surname = "Иванова",
                description = "UI/UX Designer, специализируется на создании интуитивных интерфейсов",
                imageUrl = "https://picsum.photos/300/180?random=5",
            )

            // Host card with long description
            UIKitHostCard(
                title = "Спикер",
                name = "Дмитрий",
                surname = "Сидоров",
                description = "Team Lead и архитектор, ведет команду из 12 разработчиков. " +
                    "Автор популярных статей на Хабре и докладчик на конференциях",
                imageUrl = "https://picsum.photos/300/180?random=5",
                onCardClick = { /* Handle click */ },
            )
        }
    }
}
