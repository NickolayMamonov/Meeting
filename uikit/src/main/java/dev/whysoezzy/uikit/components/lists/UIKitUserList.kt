package dev.whysoezzy.uikit.components.lists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.whysoezzy.uikit.components.avatars.UIKitAvatar
import dev.whysoezzy.uikit.components.text.TextBody1
import dev.whysoezzy.uikit.components.text.TextMetadata2
import dev.whysoezzy.uikit.models.UIKitPerson
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.ColorTokens
import dev.whysoezzy.uikit.tokens.SpacingTokens

@Composable
fun UIKitUserList(
    users: List<UIKitPerson>,
    modifier: Modifier = Modifier,
    onUserClick: (UIKitPerson) -> Unit = {}
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.S)
    ) {
        items(users, key = {it.id}) { user ->
            UIKitUserListItem(
                user = user,
                onClick = { onUserClick(user) }
            )
        }
    }
}

@Composable
private fun UIKitUserListItem(
    user: UIKitPerson,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(SpacingTokens.M),
            horizontalArrangement = Arrangement.spacedBy(SpacingTokens.M),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UIKitAvatar(
                imageUrl = user.avatar,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(SpacingTokens.XS)
            ) {
                TextBody1(
                    text = "${user.name} ${user.surname}",
                    color = ColorTokens.NeutralDark
                )

                if (user.description.isNotEmpty()) {
                    TextMetadata2(
                        text = user.description.orEmpty(),
                        color = ColorTokens.NeutralWeak
                    )
                }
            }
        }

        HorizontalDivider(
            color = ColorTokens.NeutralWeak,
            thickness = 1.dp
        )
    }
}

@Preview
@Composable
private fun UIKitUserListPreview() {
    UIKitTheme {
        UIKitUserList(
            users = listOf(
                UIKitPerson(
                    id = 1,
                    name = "Иван",
                    surname = "Петров",
                    avatar = "",
                    description = "Android разработчик"
                ),
                UIKitPerson(
                    id = 2,
                    name = "Мария",
                    surname = "Сидорова",
                    avatar = "",
                    description = "UI/UX дизайнер"
                ),
                UIKitPerson(
                    id = 3,
                    name = "Александр",
                    surname = "Козлов",
                    avatar = "",
                    description = ""
                )
            )
        )
    }
}
