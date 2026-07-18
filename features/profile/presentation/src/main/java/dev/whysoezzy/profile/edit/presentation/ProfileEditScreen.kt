package dev.whysoezzy.profile.edit.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import com.whysoezzy.domain.models.SocialMediaType
import dev.whysoezzy.profile.R
import dev.whysoezzy.uikit.components.inputs.UIKitInput
import dev.whysoezzy.uikit.components.tags.UIKitTagGroup
import dev.whysoezzy.uikit.components.tags.UIKitTagSize
import dev.whysoezzy.uikit.components.toggles.UIKitToggleRow
import dev.whysoezzy.uikit.components.topbar.EditTopBar
import dev.whysoezzy.uikit.error.asUserMessage
import dev.whysoezzy.uikit.security.SecureScreenEffect
import dev.whysoezzy.uikit.tokens.BorderRadiusTokens
import dev.whysoezzy.uikit.tokens.ColorTokens
import dev.whysoezzy.uikit.tokens.SFProDisplayFontFamily
import dev.whysoezzy.uikit.tokens.SpacingTokens
import org.koin.androidx.compose.koinViewModel
import dev.whysoezzy.uikit.R as UIKitR

@Composable
fun ProfileEditScreen(
    modifier: Modifier = Modifier,
    onBackPressed: () -> Unit,
    onSaveSuccess: () -> Unit,
    onEditInterests: (List<String>) -> Unit = {},
    viewModel: ProfileEditViewModel = koinViewModel(),
) {
    SecureScreenEffect()

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    var showInterestDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.navEvent.collect { event ->
                when (event) {
                    is ProfileEditNavEvent.NavigateBack -> onSaveSuccess()
                    is ProfileEditNavEvent.NavigateToAuth -> onSaveSuccess()
                }
            }
        }
    }

    val errorMessage = uiState.error?.asUserMessage()
    // Показываем ошибку в Snackbar
    LaunchedEffect(errorMessage) {
        errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Форма всегда показывается — пустые поля отображают hint-тексты,
            // структура экрана (аватар-заглушка, секции, тогглы) видна с первого кадра
            EditContent(
                uiState = uiState,
                onAvatarClick = { viewModel.onEvent(ProfileEditEvent.ChangeAvatar) },
                onNameSurnameChange = { viewModel.onEvent(ProfileEditEvent.UpdateNameSurname(it)) },
                onCityChange = { viewModel.onEvent(ProfileEditEvent.UpdateCity(it)) },
                onDescriptionChange = { viewModel.onEvent(ProfileEditEvent.UpdateDescription(it)) },
                onAddInterest = { showInterestDialog = true },
                onRemoveInterest = { viewModel.onEvent(ProfileEditEvent.RemoveInterest(it)) },
                onSocialMediaChange = { type, username ->
                    viewModel.onEvent(ProfileEditEvent.UpdateSocialMedia(type, username))
                },
                onToggleShowCommunities = { viewModel.onEvent(ProfileEditEvent.ToggleShowCommunities) },
                onToggleShowMeetings = { viewModel.onEvent(ProfileEditEvent.ToggleShowMeetings) },
                onToggleNotifications = { viewModel.onEvent(ProfileEditEvent.ToggleNotifications) },
                onDeleteProfile = { viewModel.onEvent(ProfileEditEvent.DeleteProfile) },
            )

            // Лёгкий оверлей-спиннер во время первоначальной загрузки данных
            if (uiState.isLoading && uiState.name.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }

            EditTopBar(
                title = "",
                onCancelClick = onBackPressed,
                onSaveClick = { viewModel.onEvent(ProfileEditEvent.Save) },
                isSaveEnabled = uiState.isValid && !uiState.isSaving,
                containerColor = Color.Transparent,
                contentColor = Color.White,
                applyStatusBarPadding = true,
            )
        }

        if (showInterestDialog) {
            AlertDialog(
                onDismissRequest = { showInterestDialog = false },
                title = { Text(stringResource(R.string.profile_edit_interests_title)) },
                text = {
                    if (uiState.availableTags.isEmpty()) {
                        Text(stringResource(R.string.profile_edit_interests_loading))
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(uiState.availableTags.entries.toList(), key = { (tagId, _) -> tagId }) { (tagId, tagName) ->
                                val isSelected = uiState.interests.contains(tagName)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.onEvent(ProfileEditEvent.ToggleTag(tagId, tagName))
                                        }.padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = {
                                            viewModel.onEvent(ProfileEditEvent.ToggleTag(tagId, tagName))
                                        },
                                    )
                                    Text(
                                        text = tagName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(start = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showInterestDialog = false }) { Text(stringResource(dev.whysoezzy.uikit.R.string.action_done)) }
                },
            )
        }

        if (uiState.showDeleteConfirmDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.onEvent(ProfileEditEvent.DismissDeleteProfile) },
                title = { Text(stringResource(R.string.profile_edit_delete_title)) },
                text = { Text(stringResource(R.string.profile_edit_delete_message)) },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.onEvent(ProfileEditEvent.ConfirmDeleteProfile) },
                        enabled = !uiState.isSaving,
                    ) {
                        Text(stringResource(R.string.profile_edit_delete_confirm), color = ColorTokens.AccentDanger)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.onEvent(ProfileEditEvent.DismissDeleteProfile) }) {
                        Text(stringResource(dev.whysoezzy.uikit.R.string.action_cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun EditContent(
    uiState: ProfileEditUiState,
    onAvatarClick: () -> Unit,
    onNameSurnameChange: (String) -> Unit,
    onCityChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onAddInterest: () -> Unit,
    onRemoveInterest: (String) -> Unit,
    onSocialMediaChange: (SocialMediaType, String) -> Unit,
    onToggleShowCommunities: () -> Unit,
    onToggleShowMeetings: () -> Unit,
    onToggleNotifications: () -> Unit,
    onDeleteProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        // 1. Фото + кнопка "Изменить фото"
        item {
            Box(
                modifier = Modifier.fillMaxWidth().height(280.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                if (uiState.avatarUrl != null) {
                    AsyncImage(
                        model = uiState.avatarUrl,
                        contentDescription = stringResource(R.string.profile_edit_photo_content_description),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                        error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
                Box(
                    modifier = Modifier
                        .padding(bottom = SpacingTokens.M)
                        .clip(RoundedCornerShape(BorderRadiusTokens.S))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable { onAvatarClick() }
                        .padding(horizontal = SpacingTokens.M, vertical = SpacingTokens.S),
                ) {
                    Text(
                        text = stringResource(R.string.profile_edit_photo_change),
                        color = Color.White,
                        fontFamily = SFProDisplayFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                    )
                }
            }
        }

        // 2. Основные поля
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpacingTokens.L)
                    .padding(top = SpacingTokens.M),
                verticalArrangement = Arrangement.spacedBy(SpacingTokens.M),
            ) {
                // Единое поле "Имя Фамилия"
                UIKitInput(
                    value = uiState.nameSurname,
                    onValueChange = onNameSurnameChange,
                    hint = stringResource(R.string.profile_edit_field_fullname),
                    isError = uiState.nameError != null,
                    errorMessage = uiState.nameError ?: "",
                    modifier = Modifier.fillMaxWidth(),
                )

                // Телефон — readOnly
                UIKitInput(
                    value = uiState.phone,
                    onValueChange = {},
                    hint = "+7 000 000-00-00",
                    modifier = Modifier.fillMaxWidth(),
                )

                // Город
                UIKitInput(
                    value = uiState.city,
                    onValueChange = onCityChange,
                    hint = stringResource(R.string.profile_edit_field_city),
                    modifier = Modifier.fillMaxWidth(),
                )

                // О себе
                UIKitInput(
                    value = uiState.description,
                    onValueChange = onDescriptionChange,
                    hint = stringResource(R.string.profile_edit_field_about),
                    isError = uiState.descriptionError != null,
                    errorMessage = uiState.descriptionError ?: "",
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
            }
        }

        // 3. Интересы
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpacingTokens.L)
                    .padding(top = SpacingTokens.L),
                verticalArrangement = Arrangement.spacedBy(SpacingTokens.S),
            ) {
                Text(
                    text = stringResource(R.string.profile_edit_section_interests),
                    fontFamily = SFProDisplayFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp,
                    color = ColorTokens.NeutralActive,
                )
                if (uiState.interests.isNotEmpty()) {
                    UIKitTagGroup(
                        tags = uiState.interests,
                        selectedTags = uiState.interests.toSet(),
                        size = UIKitTagSize.MEDIUM,
                        onTagClick = onRemoveInterest,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    text = "+ Добавить",
                    fontFamily = SFProDisplayFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp,
                    color = ColorTokens.BrandDefault,
                    modifier = Modifier.clickable { onAddInterest() },
                )
            }
        }

        // 4. Социальные сети — Хабр и Телеграм
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpacingTokens.L)
                    .padding(top = SpacingTokens.L),
                verticalArrangement = Arrangement.spacedBy(SpacingTokens.M),
            ) {
                Text(
                    text = stringResource(R.string.profile_edit_section_social),
                    fontFamily = SFProDisplayFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp,
                    color = ColorTokens.NeutralActive,
                )
                SocialField(
                    icon = painterResource(UIKitR.drawable.habr_icon),
                    value = uiState.socialMedias[SocialMediaType.HABR] ?: "",
                    onValueChange = { onSocialMediaChange(SocialMediaType.HABR, it) },
                    hint = stringResource(R.string.profile_edit_social_habr),
                )
                SocialField(
                    icon = painterResource(UIKitR.drawable.telegram_logo),
                    value = uiState.socialMedias[SocialMediaType.TELEGRAM] ?: "",
                    onValueChange = { onSocialMediaChange(SocialMediaType.TELEGRAM, it) },
                    hint = stringResource(R.string.profile_edit_social_telegram),
                )
            }
        }

        // 5. Настройки
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpacingTokens.L)
                    .padding(top = SpacingTokens.L),
                verticalArrangement = Arrangement.spacedBy(SpacingTokens.S),
            ) {
                UIKitToggleRow(
                    label = stringResource(R.string.profile_edit_toggle_show_communities),
                    checked = uiState.showCommunities,
                    onCheckedChange = { onToggleShowCommunities() },
                )
                UIKitToggleRow(
                    label = stringResource(R.string.profile_edit_toggle_show_meetings),
                    checked = uiState.showMeetings,
                    onCheckedChange = { onToggleShowMeetings() },
                )
                UIKitToggleRow(
                    label = stringResource(R.string.profile_edit_toggle_notifications),
                    checked = uiState.notificationsEnabled,
                    onCheckedChange = { onToggleNotifications() },
                )
            }
        }

        // 6. Удалить профиль
        item {
            Spacer(modifier = Modifier.height(SpacingTokens.XL))
            Text(
                text = stringResource(R.string.profile_edit_delete_profile),
                fontFamily = SFProDisplayFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                color = ColorTokens.AccentDanger,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDeleteProfile() }
                    .padding(vertical = SpacingTokens.M),
            )
        }

        item {
            Spacer(modifier = Modifier.height(SpacingTokens.XL).navigationBarsPadding())
        }
    }
}

@Composable
private fun SocialField(
    icon: Painter,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpacingTokens.M),
    ) {
        Icon(
            painter = icon,
            contentDescription = hint,
            tint = ColorTokens.NeutralWeak,
            modifier = Modifier.size(20.dp),
        )
        UIKitInput(
            value = value,
            onValueChange = onValueChange,
            hint = hint,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
