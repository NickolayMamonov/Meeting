package dev.whysoezzy.profile.details.presentation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.whysoezzy.profile.R
import dev.whysoezzy.uikit.components.blocks.UIKitUserCommunitiesBlock
import dev.whysoezzy.uikit.components.blocks.UIKitUserMeetingsBlock
import dev.whysoezzy.uikit.components.blocks.UIKitUserProfileBlock
import dev.whysoezzy.uikit.components.buttons.UIKitButton
import dev.whysoezzy.uikit.components.social.UIKitSocialMediaList
import dev.whysoezzy.uikit.components.text.TextBody1
import dev.whysoezzy.uikit.components.topbar.ProfileTopBar
import dev.whysoezzy.uikit.tokens.ColorTokens
import dev.whysoezzy.uikit.tokens.SFProDisplayFontFamily
import dev.whysoezzy.uikit.tokens.SpacingTokens
import org.koin.androidx.compose.koinViewModel
import timber.log.Timber
import dev.whysoezzy.uikit.R as UIKitR

private val ErrorButtonMaxWidth = 343.dp

@Composable
fun ProfileDetailsScreen(
    modifier: Modifier = Modifier,
    userId: Long? = null,
    onBackPressed: () -> Unit,
    onEditClick: () -> Unit = {},
    onLogout: () -> Unit = {},
    onNameInput: () -> Unit = {},
    onMeetingClick: (Long) -> Unit = {},
    onCommunityClick: (Long) -> Unit = {},
    viewModel: ProfileDetailsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    val context = LocalContext.current

    LaunchedEffect(userId) {
        viewModel.onEvent(ProfileDetailsEvent.LoadProfile(userId))
    }

    LaunchedEffect(Unit) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.navEvent.collect { event ->
                when (event) {
                    is ProfileDetailsNavEvent.NavigateToAuth -> onLogout()
                    is ProfileDetailsNavEvent.NavigateToNameInput -> onNameInput()
                    is ProfileDetailsNavEvent.NavigateToEdit -> onEditClick()
                    is ProfileDetailsNavEvent.NavigateToMeeting -> onMeetingClick(event.meetingId)
                    is ProfileDetailsNavEvent.NavigateToCommunity -> onCommunityClick(event.communityId)
                    is ProfileDetailsNavEvent.OpenSocialMedia -> openUrlIntent(context, event.url)
                    is ProfileDetailsNavEvent.ShareProfile -> shareProfileIntent(context, event.name, event.shareText)
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (val state = uiState) {
            is ProfileDetailsUiState.Loading -> LoadingContent()

            is ProfileDetailsUiState.Success -> ProfileContent(
                uiState = state,
                onMeetingClick = { viewModel.onEvent(ProfileDetailsEvent.NavigateToMeeting(it)) },
                onCommunityClick = { viewModel.onEvent(ProfileDetailsEvent.NavigateToCommunity(it)) },
                onSocialMediaClick = { viewModel.onEvent(ProfileDetailsEvent.OpenSocialMedia(it)) },
                onCommunitySubscribeClick = { id, subscribed ->
                    viewModel.onEvent(ProfileDetailsEvent.ToggleCommunitySubscription(id, subscribed))
                },
                onLogoutClick = { viewModel.onEvent(ProfileDetailsEvent.Logout) },
            )

            is ProfileDetailsUiState.Error -> ErrorContent(
                message = state.message,
                onRetry = { viewModel.onEvent(ProfileDetailsEvent.LoadProfile(userId)) },
                onBackPressed = onBackPressed,
            )
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
            applyStatusBarPadding = true,
        )
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
    ) {
        // 1. Фото + основная инфо — edge-to-edge, без верхнего padding
        item {
            UIKitUserProfileBlock(
                name = uiState.name,
                surname = uiState.surname,
                city = uiState.city,
                description = uiState.description.ifBlank {
                    if (uiState.isOwnProfile) stringResource(R.string.profile_details_description_placeholder_self) else ""
                },
                avatarUrl = uiState.avatarUrl,
                interests = uiState.interests,
                coverHeight = 375,
            )
        }

        // 2. Соцсети
        if (uiState.socialMedias.isNotEmpty()) {
            item {
                UIKitSocialMediaList(
                    socialMedias = uiState.socialMedias,
                    onSocialMediaClick = onSocialMediaClick,
                    modifier = Modifier.padding(
                        horizontal = SpacingTokens.L,
                        vertical = SpacingTokens.M,
                    ),
                )
            }
        }

        // 3. Встречи — для своего профиля показываем всегда (с заглушкой если пусто)
        if (uiState.isOwnProfile || uiState.userMeetings.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(SpacingTokens.M))
                UIKitUserMeetingsBlock(
                    title = if (uiState.isOwnProfile) {
                        stringResource(R.string.profile_details_meetings_self)
                    } else {
                        stringResource(R.string.profile_details_meetings_other)
                    },
                    meetings = uiState.userMeetings,
                    onMeetingClick = onMeetingClick,
                    modifier = Modifier.padding(horizontal = SpacingTokens.L),
                )
            }
        }

        // 4. Сообщества — для своего профиля показываем всегда (с заглушкой если пусто)
        if (uiState.isOwnProfile || uiState.userCommunities.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(SpacingTokens.M))
                UIKitUserCommunitiesBlock(
                    title = if (uiState.isOwnProfile) {
                        stringResource(
                            R.string.profile_details_communities_self,
                        )
                    } else {
                        stringResource(R.string.profile_details_communities_other)
                    },
                    communities = uiState.userCommunities,
                    subscribedCommunityIds = emptySet(),
                    onCommunityClick = onCommunityClick,
                    onSubscribeClick = { _, _ -> },
                    modifier = Modifier.padding(horizontal = SpacingTokens.L),
                )
            }
        }

        // 5. Кнопка "Выйти" — только для своего профиля, текстовая по дизайну
        if (uiState.isOwnProfile) {
            item {
                Spacer(modifier = Modifier.height(SpacingTokens.XL))
                Text(
                    text = stringResource(R.string.profile_details_logout),
                    fontFamily = SFProDisplayFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 18.sp,
                    color = ColorTokens.NeutralWeak, // #A4A4A4 — серый по дизайну
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLogoutClick() }
                        .padding(vertical = SpacingTokens.M),
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(SpacingTokens.L).navigationBarsPadding())
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.M),
        ) {
            TextBody1(text = message, textAlign = TextAlign.Center)
            UIKitButton(
                text = stringResource(UIKitR.string.action_retry),
                onClick = onRetry,
                modifier = Modifier.widthIn(max = ErrorButtonMaxWidth).fillMaxWidth(),
            )
            UIKitButton(
                text = stringResource(UIKitR.string.action_back),
                onClick = onBackPressed,
                modifier = Modifier.widthIn(max = ErrorButtonMaxWidth).fillMaxWidth(),
            )
        }
    }
}

private fun openUrlIntent(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (e: ActivityNotFoundException) {
        Timber.w(e, "No activity to handle URL: %s", url)
    }
}

private fun shareProfileIntent(context: Context, name: String, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, name)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.profile_details_share_chooser_title)))
}
