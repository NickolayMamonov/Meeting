package dev.whysoezzy.profile.details.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import dev.whysoezzy.uikit.components.blocks.UIKitUserCommunitiesBlock
import dev.whysoezzy.uikit.components.blocks.UIKitUserMeetingsBlock
import dev.whysoezzy.uikit.components.blocks.UIKitUserProfileBlock
import dev.whysoezzy.uikit.components.buttons.UIKitButton
import dev.whysoezzy.uikit.components.social.UIKitSocialMediaList
import dev.whysoezzy.uikit.components.text.TextBody1
import dev.whysoezzy.uikit.components.topbar.ProfileTopBar
import dev.whysoezzy.uikit.tokens.SpacingTokens
import org.koin.androidx.compose.koinViewModel
@Composable
fun ProfileDetailsScreen(
    modifier: Modifier = Modifier,
    userId: Long? = null,
    onBackPressed: () -> Unit,
    onEditClick: () -> Unit = {},
    onMeetingClick: (Long) -> Unit = {},
    onCommunityClick: (Long) -> Unit = {},
    onSocialMediaClick: (String) -> Unit = {},
    onCommunitySubscribeClick: (Long, Boolean) -> Unit = { _, _ -> },
    viewModel: ProfileDetailsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(userId) {
        viewModel.onEvent(ProfileDetailsEvent.LoadProfile(userId))
    }
    Box(modifier = Modifier.fillMaxSize()) {
        when (uiState) {
            is ProfileDetailsUiState.Loading -> {
                LoadingContent()
            }

            is ProfileDetailsUiState.Success -> {
                ProfileContent(
                    uiState = uiState as ProfileDetailsUiState.Success,
                    onMeetingClick = onMeetingClick,
                    onCommunityClick = onCommunityClick,
                    onSocialMediaClick = onSocialMediaClick,
                    onCommunitySubscribeClick = { communityId, isSubscribed ->
                        viewModel.onEvent(
                            ProfileDetailsEvent.ToggleCommunitySubscription(
                                communityId,
                                isSubscribed
                            )
                        )
                    }
                )
            }

            is ProfileDetailsUiState.Error -> {
                ErrorContent(
                    message = (uiState as ProfileDetailsUiState.Error).message,
                    onRetry = { viewModel.onEvent(ProfileDetailsEvent.LoadProfile(userId)) },
                    onBackPressed = onBackPressed
                )
            }
        }

        when (uiState) {
            is ProfileDetailsUiState.Success -> {
                ProfileTopBar(
                    title = "",
                    isOwnProfile = (uiState as ProfileDetailsUiState.Success).isOwnProfile,
                    onBackClick = onBackPressed,
                    onEditClick = onEditClick,
                    onShareClick = { viewModel.onEvent(ProfileDetailsEvent.ShareProfile) },
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                    applyStatusBarPadding = false
                )
            }

            else -> {
                ProfileTopBar(
                    title = "",
                    isOwnProfile = false,
                    onBackClick = onBackPressed,
                    containerColor = Color.Transparent,
                    applyStatusBarPadding = false
                )
            }
        }
    }
}

@Composable
private fun LoadingContent(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ProfileContent(
    uiState: ProfileDetailsUiState.Success,
    onMeetingClick: (Long) -> Unit,
    onCommunityClick: (Long) -> Unit,
    onSocialMediaClick: (String) -> Unit,
    onCommunitySubscribeClick: (Long, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize()
    ) {
        item {
            UIKitUserProfileBlock(
                name = uiState.name,
                surname = uiState.surname,
                city = uiState.city,
                description = uiState.description,
                avatarUrl = uiState.avatarUrl,
                interests = uiState.interests
            )
        }

        item {
            Column(
                modifier = Modifier.padding(horizontal = SpacingTokens.L),
                verticalArrangement = Arrangement.spacedBy(SpacingTokens.L)
            ) {
                // Социальные сети
                if (uiState.socialMedias.isNotEmpty()) {
                    UIKitSocialMediaList(
                        socialMedias = uiState.socialMedias,
                        onSocialMediaClick = onSocialMediaClick
                    )
                }
            }
        }

        if (uiState.userMeetings.isNotEmpty()) {
            item {
                UIKitUserMeetingsBlock(
                    title = if (uiState.isOwnProfile) "Мои встречи" else "Встречи",
                    meetings = uiState.userMeetings,
                    onMeetingClick = onMeetingClick,
                    modifier = Modifier.padding(horizontal = SpacingTokens.L)
                )
            }
        }

        if (uiState.userCommunities.isNotEmpty()) {
            item {
                UIKitUserCommunitiesBlock(
                    title = if (uiState.isOwnProfile) "Мои сообщества" else "Сообщества",
                    communities = uiState.userCommunities,
                    subscribedCommunityIds = uiState.subscribedCommunityIds,
                    onCommunityClick = onCommunityClick,
                    onSubscribeClick = onCommunitySubscribeClick,
                    modifier = Modifier.padding(horizontal = SpacingTokens.L)
                )
            }
        }

        if (uiState.isOwnProfile) {
            item {
                Spacer(modifier = Modifier.height(SpacingTokens.L))
            }

            item {
                UIKitButton(
                    text = "Выйти",
                    onClick = { /* TODO: logout logic */ },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SpacingTokens.L)
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(SpacingTokens.L))
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
        ) {
            TextBody1(
                text = message,
                textAlign = TextAlign.Center
            )

            UIKitButton(
                text = "Повторить",
                onClick = onRetry
            )

            UIKitButton(
                text = "Назад",
                onClick = onBackPressed
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun ProfileDetailsScreenLoadingPreview() {
    dev.whysoezzy.uikit.theme.UIKitTheme {
        androidx.compose.material3.Surface {
            LoadingContent()
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun ProfileDetailsScreenSuccessPreview() {
    dev.whysoezzy.uikit.theme.UIKitTheme {
        androidx.compose.material3.Surface {
            ProfileContent(
                uiState = ProfileDetailsUiState.Success(
                    userId = 1,
                    name = "Сергей",
                    surname = "",
                    email = "sergey@example.com",
                    city = "Москва",
                    description = "Занимаюсь разработкой интерфейсов в еСот. Учу HTML, CSS и JavaScript",
                    avatarUrl = "https://picsum.photos/200/200?random=1",
                    interests = listOf("Разработка", "Дизайн", "Illustrator", "Backend", "Продакт менеджмент"),
                    isOwnProfile = true,
                    socialMedias = listOf(
                        dev.whysoezzy.uikit.models.UIKitSocialMediaInfo(
                            type = dev.whysoezzy.uikit.models.UIKitSocialMedia.TELEGRAM,
                            url = "https://t.me/username",
                            username = "@username"
                        ),
                        dev.whysoezzy.uikit.models.UIKitSocialMediaInfo(
                            type = dev.whysoezzy.uikit.models.UIKitSocialMedia.HABR,
                            url = "https://habr.com/users/username",
                            username = "username"
                        )
                    ),
                    userMeetings = listOf(
                        dev.whysoezzy.uikit.models.UIKitMeetingInfo(
                            id = 1,
                            imageUrl = "https://picsum.photos/212/148?random=1",
                            title = "Android Dev Meetup",
                            date = "15 марта, 19:00",
                            address = "ул. Тверская, 15",
                            tags = listOf(
                                dev.whysoezzy.uikit.models.UIKitMeetingTag(
                                    id = 1,
                                    text = "Android",
                                    state = dev.whysoezzy.uikit.models.UIKitTagState.ACTIVE
                                )
                            )
                        )
                    ),
                    userCommunities = listOf(
                        dev.whysoezzy.uikit.models.UIKitCommunityInfo(
                            id = 1,
                            imageUrl = "https://picsum.photos/300/300?random=1",
                            title = "Android Developers Moscow",
                            isSubscribed = true,
                            onSubscribeClick = {},
                            onCardClick = {}
                        )
                    ),
                    subscribedCommunityIds = setOf(1)
                ),
                onMeetingClick = {},
                onCommunityClick = {},
                onSocialMediaClick = {},
                onCommunitySubscribeClick = { _, _ -> }
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun ProfileDetailsScreenErrorPreview() {
    dev.whysoezzy.uikit.theme.UIKitTheme {
        androidx.compose.material3.Surface {
            ErrorContent(
                message = "Не удалось загрузить профиль",
                onRetry = {},
                onBackPressed = {}
            )
        }
    }
}
