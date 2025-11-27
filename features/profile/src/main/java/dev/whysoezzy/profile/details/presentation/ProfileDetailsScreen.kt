package dev.whysoezzy.profile.details.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import dev.whysoezzy.domain.models.CommunityInfo
import dev.whysoezzy.domain.models.MeetingInfo
import dev.whysoezzy.domain.models.MeetingStatus
import dev.whysoezzy.domain.models.MeetingTag
import dev.whysoezzy.domain.models.SocialMediaInfo
import dev.whysoezzy.domain.models.SocialMediaType
import dev.whysoezzy.domain.models.TagState
import dev.whysoezzy.uikit.components.blocks.UIKitUserCommunitiesBlock
import dev.whysoezzy.uikit.components.blocks.UIKitUserMeetingsBlock
import dev.whysoezzy.uikit.components.blocks.UIKitUserProfileBlock
import dev.whysoezzy.uikit.components.buttons.UIKitButton
import dev.whysoezzy.uikit.components.social.UIKitSocialMediaList
import dev.whysoezzy.uikit.components.text.TextBody1
import dev.whysoezzy.uikit.components.topbar.ProfileTopBar
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.SpacingTokens
import org.koin.androidx.compose.koinViewModel

@Composable
fun ProfileDetailsScreen(
    userId: Long? = null, // null означает собственный профиль
    onBackPressed: () -> Unit,
    onEditClick: () -> Unit = {},
    onMeetingClick: (Long) -> Unit = {},
    onCommunityClick: (Long) -> Unit = {},
    onSocialMediaClick: (String) -> Unit = {},
    onCommunitySubscribeClick: (Long, Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    viewModel: ProfileDetailsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(userId) {
        viewModel.onEvent(ProfileDetailsEvent.LoadProfile(userId))
    }

    Scaffold(
        topBar = {
            when (uiState) {
                is ProfileDetailsUiState.Success -> {
                    ProfileTopBar(
                        title = "${(uiState as ProfileDetailsUiState.Success).name} ${(uiState as ProfileDetailsUiState.Success).surname}",
                        isOwnProfile = (uiState as ProfileDetailsUiState.Success).isOwnProfile,
                        onBackClick = onBackPressed,
                        onEditClick = { viewModel.onEvent(ProfileDetailsEvent.EditProfile) },
                        onShareClick = { viewModel.onEvent(ProfileDetailsEvent.ShareProfile) }
                    )
                }

                else -> {
                    ProfileTopBar(
                        title = "",
                        isOwnProfile = false,
                        onBackClick = onBackPressed
                    )
                }
            }
        }
    ) { paddingValues ->
        when (uiState) {
            is ProfileDetailsUiState.Loading -> {
                LoadingContent(modifier = Modifier.padding(paddingValues))
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
                    },
                    modifier = Modifier.padding(paddingValues)
                )
            }

            is ProfileDetailsUiState.Error -> {
                ErrorContent(
                    message = (uiState as ProfileDetailsUiState.Error).message,
                    onRetry = { viewModel.onEvent(ProfileDetailsEvent.LoadProfile(userId)) },
                    onBackPressed = onBackPressed,
                    modifier = Modifier.padding(paddingValues)
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
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = SpacingTokens.L),
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.L)
    ) {
        // Отступ от топбара
        item {
            Spacer(modifier = Modifier.height(SpacingTokens.S))
        }

        // 1. Основная информация о пользователе (аватар, имя, описание)
        item {
            UIKitUserProfileBlock(
                name = uiState.name,
                surname = uiState.surname,
                description = uiState.description,
                avatarUrl = uiState.avatarUrl
            )
        }

        // 2. Социальные сети
        if (uiState.socialMedias.isNotEmpty()) {
            item {
                UIKitSocialMediaList(
                    socialMedias = uiState.socialMedias,
                    onSocialMediaClick = onSocialMediaClick
                )
            }
        }

        // 3. Встречи пользователя
        if (uiState.userMeetings.isNotEmpty()) {
            item {
                UIKitUserMeetingsBlock(
                    title = if (uiState.isOwnProfile) "Мои встречи" else "Встречи",
                    meetings = uiState.userMeetings,
                    onMeetingClick = onMeetingClick
                )
            }
        }

        // 4. Сообщества пользователя
        if (uiState.userCommunities.isNotEmpty()) {
            item {
                UIKitUserCommunitiesBlock(
                    title = if (uiState.isOwnProfile) "Мои сообщества" else "Сообщества",
                    communities = uiState.userCommunities,
                    subscribedCommunityIds = uiState.subscribedCommunityIds,
                    onCommunityClick = onCommunityClick,
                    onSubscribeClick = { communityId, isSubscribed ->
                        // Передаем событие в ViewModel
                        // Здесь нужно бы добавить параметр в ProfileContent
                        // онСоммунитисабскрибеКлик(communityId, isSubscribed)
                    }
                )
            }
        }

        // Нижний отступ
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

@Preview
@Composable
private fun ProfileDetailsScreenPreview() {
    UIKitTheme {
        val mockTags = listOf(
            MeetingTag(1, "Android", TagState.ACTIVE),
            MeetingTag(2, "Kotlin", TagState.ACTIVE)
        )

        val mockMeetings = listOf(
            MeetingInfo(
                id = 1,
                imageUrl = "https://picsum.photos/212/148?random=1",
                title = "Android Dev Meetup",
                address = "ул. Тверская, 15",
                tags = mockTags,
                time = System.currentTimeMillis(),
                meetingStatus = MeetingStatus.ACTIVE
            ),
            MeetingInfo(
                id = 2,
                imageUrl = "https://picsum.photos/212/148?random=2",
                title = "Kotlin Conf",
                address = "ул. Пушкина, 10",
                tags = mockTags.take(1),
                time = System.currentTimeMillis() + 86400000,
                meetingStatus = MeetingStatus.ACTIVE
            )
        )

        val mockCommunities = listOf(
            CommunityInfo(
                id = 1,
                title = "Android Developers Moscow",
                description = "Сообщество разработчиков Android в Москве",
                imageUrl = "https://picsum.photos/300/300?random=1",
                membersCount = 1250
            ),
            CommunityInfo(
                id = 2,
                title = "Kotlin User Group",
                description = "Kotlin enthusiasts",
                imageUrl = "https://picsum.photos/300/300?random=2",
                membersCount = 890
            )
        )

        val mockSocialMedias = listOf(
            SocialMediaInfo(
                type = SocialMediaType.TELEGRAM,
                url = "https://t.me/username",
                username = "@username"
            ),
            SocialMediaInfo(
                type = SocialMediaType.HABR,
                url = "https://habr.com/users/username",
                username = "username"
            )
        )

        ProfileContent(
            uiState = ProfileDetailsUiState.Success(
                userId = 1,
                name = "Иван",
                surname = "Петров",
                email = "ivan.petrov@example.com",
                description = "Senior Android Developer в крутой компании. Люблю изучать новые технологии и делиться знаниями с сообществом.",
                avatarUrl = "https://picsum.photos/200/200?random=1",
                isOwnProfile = true,
                socialMedias = mockSocialMedias,
                userMeetings = mockMeetings,
                userCommunities = mockCommunities
            ),
            onMeetingClick = { },
            onCommunityClick = { },
            onSocialMediaClick = { },
            onCommunitySubscribeClick = { _, _ -> },
        )
    }
}