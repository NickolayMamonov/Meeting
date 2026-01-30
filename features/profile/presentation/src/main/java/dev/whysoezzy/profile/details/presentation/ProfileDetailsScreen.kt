package dev.whysoezzy.profile.details.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlin.collections.isNotEmpty

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

        item {
            UIKitUserProfileBlock(
                name = uiState.name,
                surname = uiState.surname,
                description = uiState.description,
                avatarUrl = uiState.avatarUrl
            )
        }

        if (uiState.socialMedias.isNotEmpty()) {
            item {
                UIKitSocialMediaList(
                    socialMedias = uiState.socialMedias,
                    onSocialMediaClick = onSocialMediaClick
                )
            }
        }


        if (uiState.userMeetings.isNotEmpty()) {
            items( items = uiState.userMeetings ) {
                UIKitUserMeetingsBlock(
                    title = if (uiState.isOwnProfile) "Мои встречи" else "Встречи",
                    meetings = uiState.userMeetings,
                    onMeetingClick = onMeetingClick
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
                    onSubscribeClick = { communityId, isSubscribed ->
                        // Передаем событие в ViewModel
                        // Здесь нужно бы добавить параметр в ProfileContent
                        // онСоммунитисабскрибеКлик(communityId, isSubscribed)
                    }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(SpacingTokens.L))
        }
    }
}

//@Composable
//private fun ProfileContent(
//    user: Person,
//    meetings: List<MeetingInfo>,
//    communities: List<CommunityInfo>,
//    onMeetingClick: (Long) -> Unit,
//    onCommunityClick: (Long) -> Unit,
//    modifier: Modifier = Modifier
//) {
//    LazyColumn(
//        modifier = modifier
//            .fillMaxSize()
//            .padding(horizontal = SpacingTokens.L),
//        verticalArrangement = Arrangement.spacedBy(SpacingTokens.L)
//    ) {
//        // 1. Профиль пользователя
//        item {
//            UserProfileSection(user = user)
//        }
//
//        // 2. Встречи пользователя
//        if (meetings.isNotEmpty()) {
//            item {
//                Text(
//                    text = "Встречи (${meetings.size})",
//                    style = MaterialTheme.typography.headlineSmall,
//                    modifier = Modifier.padding(vertical = SpacingTokens.S)
//                )
//            }
//
//            item {
//                LazyRow(
//                    horizontalArrangement = Arrangement.spacedBy(SpacingTokens.M)
//                ) {
//                    items(
//                        items = meetings,
//                    ) { meeting ->
//                        MeetingCard(
//                            meeting = meeting,
//                            onClick = { onMeetingClick(meeting.id) }
//                        )
//                    }
//                }
//            }
//        }
//
//        // 3. Сообщества пользователя
//        if (communities.isNotEmpty()) {
//            item {
//                Text(
//                    text = "Сообщества (${communities.size})",
//                    style = MaterialTheme.typography.headlineSmall,
//                    modifier = Modifier.padding(vertical = SpacingTokens.S)
//                )
//            }
//
//            item {
//                LazyRow(
//                    horizontalArrangement = Arrangement.spacedBy(SpacingTokens.M)
//                ) {
//                    items(
//                        items = communities,
//                        key = { it.id }
//                    ) { community ->
//                        CommunityCard(
//                            community = community,
//                            onClick = { onCommunityClick(community.id) }
//                        )
//                    }
//                }
//            }
//        }
//
//        // Отступ снизу
//        item {
//            Spacer(modifier = Modifier.height(SpacingTokens.XL))
//        }
//    }
//}
//
//@Composable
//private fun MeetingCard(
//    meeting: MeetingInfo,
//    onClick: () -> Unit
//) {
//    // ✅ Применяем мапперы
//    UIKitEventCard(
//        imageUrl = meeting.imageUrl,
//        title = meeting.title,
//        date = formatDate(meeting.time),
//        address = meeting.toUIKitAddress(),
//        tags = meeting.tags.toUIKitEventCardTags(),
//        cardType = UIKitEventCardType.COMPACT,
//        onCardClick = onClick
//    )
//}
//
//@Composable
//private fun CommunityCard(
//    community: CommunityInfo,
//    onClick: () -> Unit
//) {
//    val uiKitCommunity = community.toUIKitCommunity()
//
//    UIKitCommunityCard(
//        name = uiKitCommunity.name,
//        description = uiKitCommunity.description,
//        imageUrl = uiKitCommunity.imageUrl,
//        onCardClick = onClick
//    )
//}
//
//@Composable
//private fun UserProfileSection(user: Person) {
//    val uiKitPerson = user.toUIKitPerson()
//
//    Column(
//        modifier = Modifier
//            .fillMaxWidth()
//            .padding(vertical = SpacingTokens.M),
//        verticalArrangement = Arrangement.spacedBy(SpacingTokens.S)
//    ) {
//        Row(
//            horizontalArrangement = Arrangement.spacedBy(SpacingTokens.M),
//            verticalAlignment = Alignment.CenterVertically
//        ) {
//            // Аватар
//            AsyncImage(
//                model = uiKitPerson.avatar,
//                contentDescription = "User avatar",
//                modifier = Modifier
//                    .size(80.dp)
//                    .clip(CircleShape),
//                contentScale = ContentScale.Crop
//            )
//
//            // Имя и фамилия
//            Column {
//                Text(
//                    text = "${uiKitPerson.name} ${uiKitPerson.surname}",
//                    style = MaterialTheme.typography.headlineMedium
//                )
//            }
//        }
//
//        // Описание
//        if (uiKitPerson.description.isNotEmpty()) {
//            Text(
//                text = uiKitPerson.description,
//                style = MaterialTheme.typography.bodyMedium,
//                color = MaterialTheme.colorScheme.onSurfaceVariant
//            )
//        }
//    }
//}
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
//
//@Preview
//@Composable
//private fun ProfileDetailsScreenPreview() {
//    UIKitTheme {
//        val mockTags = listOf(
//            MeetingTag(1, "Android", TagState.ACTIVE),
//            MeetingTag(2, "Kotlin", TagState.ACTIVE)
//        )
//
//        val mockMeetings = listOf(
//            MeetingInfo(
//                id = 1,
//                imageUrl = "https://picsum.photos/212/148?random=1",
//                title = "Android Dev Meetup",
//                address = "ул. Тверская, 15",
//                tags = mockTags,
//                time = System.currentTimeMillis(),
//                meetingStatus = MeetingStatus.ACTIVE
//            ),
//            MeetingInfo(
//                id = 2,
//                imageUrl = "https://picsum.photos/212/148?random=2",
//                title = "Kotlin Conf",
//                address = "ул. Пушкина, 10",
//                tags = mockTags.take(1),
//                time = System.currentTimeMillis() + 86400000,
//                meetingStatus = MeetingStatus.ACTIVE
//            )
//        )
//
//        val mockCommunities = listOf(
//            CommunityInfo(
//                id = 1,
//                title = "Android Developers Moscow",
//                description = "Сообщество разработчиков Android в Москве",
//                imageUrl = "https://picsum.photos/300/300?random=1",
//                membersCount = 1250
//            ),
//            CommunityInfo(
//                id = 2,
//                title = "Kotlin User Group",
//                description = "Kotlin enthusiasts",
//                imageUrl = "https://picsum.photos/300/300?random=2",
//                membersCount = 890
//            )
//        )
//
//        val mockSocialMedias = listOf(
//            SocialMediaInfo(
//                type = SocialMediaType.TELEGRAM,
//                url = "https://t.me/username",
//                username = "@username"
//            ),
//            SocialMediaInfo(
//                type = SocialMediaType.HABR,
//                url = "https://habr.com/users/username",
//                username = "username"
//            )
//        )
//
//        ProfileContent(
//            uiState = ProfileDetailsUiState.Success(
//                userId = 1,
//                name = "Иван",
//                surname = "Петров",
//                email = "ivan.petrov@example.com",
//                description = "Senior Android Developer в крутой компании. Люблю изучать новые технологии и делиться знаниями с сообществом.",
//                avatarUrl = "https://picsum.photos/200/200?random=1",
//                isOwnProfile = true,
//                socialMedias = mockSocialMedias,
//                userMeetings = mockMeetings,
//                userCommunities = mockCommunities
//            ),
//            onMeetingClick = { },
//            onCommunityClick = { },
//            onSocialMediaClick = { },
//            onCommunitySubscribeClick = { _, _ -> },
//        )
//    }
//}