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
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
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
    onLogout: () -> Unit = {},
    onMeetingClick: (Long) -> Unit = {},
    onCommunityClick: (Long) -> Unit = {},
    viewModel: ProfileDetailsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(userId) {
        viewModel.onEvent(ProfileDetailsEvent.LoadProfile(userId))
    }

    LaunchedEffect(Unit) {
        viewModel.navEvent.collect { event ->
            when (event) {
                is ProfileDetailsNavEvent.NavigateToAuth -> onLogout()
                is ProfileDetailsNavEvent.NavigateToEdit -> onEditClick()
                is ProfileDetailsNavEvent.NavigateToMeeting -> onMeetingClick(event.meetingId)
                is ProfileDetailsNavEvent.NavigateToCommunity -> onCommunityClick(event.communityId)
                is ProfileDetailsNavEvent.OpenSocialMedia -> openUrlIntent(context, event.url)
                is ProfileDetailsNavEvent.ShareProfile -> shareProfileIntent(context, event.name, event.shareText)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (val state = uiState) {
            is ProfileDetailsUiState.Loading -> LoadingContent()

            is ProfileDetailsUiState.Success -> {
                ProfileContent(
                    uiState = state,
                    onMeetingClick = { meetingId ->
                        viewModel.onEvent(ProfileDetailsEvent.NavigateToMeeting(meetingId))
                    },
                    onCommunityClick = { communityId ->
                        viewModel.onEvent(ProfileDetailsEvent.NavigateToCommunity(communityId))
                    },
                    onSocialMediaClick = { url ->
                        viewModel.onEvent(ProfileDetailsEvent.OpenSocialMedia(url))
                    },
                    onCommunitySubscribeClick = { communityId, isSubscribed ->
                        viewModel.onEvent(
                            ProfileDetailsEvent.ToggleCommunitySubscription(communityId, isSubscribed)
                        )
                    },
                    onLogoutClick = {
                        viewModel.onEvent(ProfileDetailsEvent.Logout)
                    }
                )
            }

            is ProfileDetailsUiState.Error -> {
                ErrorContent(
                    message = state.message,
                    onRetry = { viewModel.onEvent(ProfileDetailsEvent.LoadProfile(userId)) },
                    onBackPressed = onBackPressed
                )
            }
        }

        val successState = uiState as? ProfileDetailsUiState.Success
        ProfileTopBar(
            title = "",
            isOwnProfile = successState?.isOwnProfile ?: false,
            onBackClick = onBackPressed,
            onEditClick = { viewModel.onEvent(ProfileDetailsEvent.EditProfile) },
            onShareClick = { viewModel.onEvent(ProfileDetailsEvent.ShareProfile) },
            containerColor = Color.Transparent,
            contentColor = Color.White,
            applyStatusBarPadding = false
        )
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
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
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {

        item {
            UIKitUserProfileBlock(
                name = uiState.name.ifBlank { "Укажите имя" },
                surname = uiState.surname,
                city = uiState.city,
                description = uiState.description.ifBlank {
                    if (uiState.isOwnProfile) "Добавьте описание профиля…" else ""
                },
                avatarUrl = uiState.avatarUrl,
                interests = uiState.interests
            )
        }

        item {
            Column(
                modifier = Modifier.padding(horizontal = SpacingTokens.L),
                verticalArrangement = Arrangement.spacedBy(SpacingTokens.L)
            ) {
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
            if (uiState.name.isBlank()) {
                item {
                    UIKitButton(
                        text = "Заполнить профиль",
                        onClick = { /* навигация через существующий икон EditProfile в TopBar */ },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SpacingTokens.L)
                    )
                }
                item { Spacer(modifier = Modifier.height(SpacingTokens.M)) }
            }
            item {
                UIKitButton(
                    text = "Выйти",
                    onClick = onLogoutClick,
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
            UIKitButton(text = "Повторить", onClick = onRetry)
            UIKitButton(text = "Назад", onClick = onBackPressed)
        }
    }
}

private fun openUrlIntent(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (_: Exception) { }
}

private fun shareProfileIntent(context: Context, name: String, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, name)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Поделиться профилем"))
}

// region Previews

@Preview
@Composable
private fun LoadingPreview() {
    dev.whysoezzy.uikit.theme.UIKitTheme {
        androidx.compose.material3.Surface { LoadingContent() }
    }
}

@Preview
@Composable
private fun SuccessPreview() {
    dev.whysoezzy.uikit.theme.UIKitTheme {
        androidx.compose.material3.Surface {
            ProfileContent(
                uiState = ProfileDetailsUiState.Success(
                    userId = 1,
                    name = "Сергей",
                    surname = "",
                    email = "sergey@example.com",
                    city = "Москва",
                    description = "Занимаюсь разработкой интерфейсов",
                    avatarUrl = "https://picsum.photos/200/200?random=1",
                    interests = listOf("Разработка", "Дизайн", "Backend"),
                    isOwnProfile = true,
                    socialMedias = listOf(
                        dev.whysoezzy.uikit.models.UIKitSocialMediaInfo(
                            type = dev.whysoezzy.uikit.models.UIKitSocialMedia.TELEGRAM,
                            url = "https://t.me/username",
                            username = "@username"
                        )
                    ),
                    userMeetings = emptyList(),
                    userCommunities = emptyList(),
                    subscribedCommunityIds = emptySet()
                ),
                onMeetingClick = {},
                onCommunityClick = {},
                onSocialMediaClick = {},
                onCommunitySubscribeClick = { _, _ -> },
                onLogoutClick = {}
            )
        }
    }
}

@Preview
@Composable
private fun ErrorPreview() {
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