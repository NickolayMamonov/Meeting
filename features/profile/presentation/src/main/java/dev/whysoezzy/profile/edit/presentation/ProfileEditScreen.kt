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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.whysoezzy.uikit.components.inputs.UIKitInput
import dev.whysoezzy.uikit.components.tags.UIKitTagGroup
import dev.whysoezzy.uikit.components.tags.UIKitTagSize
import dev.whysoezzy.uikit.components.text.TextBody2
import dev.whysoezzy.uikit.components.text.TextHeading2
import dev.whysoezzy.uikit.components.toggles.UIKitToggleRow
import dev.whysoezzy.uikit.components.topbar.EditTopBar
import dev.whysoezzy.uikit.tokens.SpacingTokens
import org.koin.androidx.compose.koinViewModel

@Composable
fun ProfileEditScreen(
    modifier: Modifier = Modifier,
    onBackPressed: () -> Unit,
    onSaveSuccess: () -> Unit,
    viewModel: ProfileEditViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var showAddInterestDialog by remember { mutableStateOf(false) }
    var newInterestText by remember { mutableStateOf("") }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onSaveSuccess()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.isLoading && uiState.name.isEmpty() -> {
                LoadingContent()
            }

            else -> {
                EditContent(
                    uiState = uiState,
                    onAvatarClick = { viewModel.onEvent(ProfileEditEvent.ChangeAvatar) },
                    onNameChange = { viewModel.onEvent(ProfileEditEvent.UpdateName(it)) },
                    onSurnameChange = { viewModel.onEvent(ProfileEditEvent.UpdateSurname(it)) },
                    onPhoneChange = { viewModel.onEvent(ProfileEditEvent.UpdatePhone(it)) },
                    onCityChange = { viewModel.onEvent(ProfileEditEvent.UpdateCity(it)) },
                    onDescriptionChange = { viewModel.onEvent(ProfileEditEvent.UpdateDescription(it)) },
                    onAddInterest = { showAddInterestDialog = true },
                    onRemoveInterest = { interest -> viewModel.onEvent(ProfileEditEvent.RemoveInterest(interest)) },
                    onSocialMediaChange = { type, username ->
                        viewModel.onEvent(ProfileEditEvent.UpdateSocialMedia(type, username))
                    },
                    onToggleShowCommunities = { viewModel.onEvent(ProfileEditEvent.ToggleShowCommunities) },
                    onToggleShowMeetings = { viewModel.onEvent(ProfileEditEvent.ToggleShowMeetings) },
                    onToggleNotifications = { viewModel.onEvent(ProfileEditEvent.ToggleNotifications) },
                    onDeleteProfile = { viewModel.onEvent(ProfileEditEvent.DeleteProfile) }
                )
            }
        }

        EditTopBar(
            title = "",
            onCancelClick = onBackPressed,
            onSaveClick = { viewModel.onEvent(ProfileEditEvent.Save) },
            isSaveEnabled = uiState.isValid && !uiState.isSaving,
            containerColor = Color.Transparent,
            contentColor = Color.White,
            applyStatusBarPadding = false
        )
    }

    if (showAddInterestDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddInterestDialog = false
                newInterestText = ""
            },
            title = { Text("Добавить интерес") },
            text = {
                UIKitInput(
                    value = newInterestText,
                    onValueChange = { newInterestText = it },
                    hint = "Например: Android разработка",
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newInterestText.isNotBlank()) {
                            viewModel.onEvent(ProfileEditEvent.AddInterestWithText(newInterestText))
                            newInterestText = ""
                            showAddInterestDialog = false
                        }
                    },
                    enabled = newInterestText.isNotBlank()
                ) {
                    Text("Добавить")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddInterestDialog = false
                    newInterestText = ""
                }) {
                    Text("Отмена")
                }
            }
        )
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
private fun EditContent(
    uiState: ProfileEditUiState,
    onAvatarClick: () -> Unit,
    onNameChange: (String) -> Unit,
    onSurnameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onCityChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onAddInterest: () -> Unit,
    onRemoveInterest: (String) -> Unit,
    onSocialMediaChange: (String, String) -> Unit,
    onToggleShowCommunities: () -> Unit,
    onToggleShowMeetings: () -> Unit,
    onToggleNotifications: () -> Unit,
    onDeleteProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize()
    ) {
        // 1. Обложка с кнопкой "Изменить фото"
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clickable { onAvatarClick() },
                contentAlignment = Alignment.Center
            ) {
                // Аватар
                if (uiState.avatarUrl != null) {
                    AsyncImage(
                        model = uiState.avatarUrl,
                        contentDescription = "Фото профиля",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }

                // Кнопка "Изменить фото"
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = SpacingTokens.M)
                        .background(
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = MaterialTheme.shapes.medium
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Изменить фото",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // 2. Основная информация
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpacingTokens.L),
                verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
            ) {
                Spacer(modifier = Modifier.height(SpacingTokens.M))

                // Имя и Фамилия
                UIKitInput(
                    value = "${uiState.name} ${uiState.surname}",
                    onValueChange = onNameChange,
                    hint = "Имя Фамилия",
                    isError = uiState.nameError != null,
                    errorMessage = uiState.nameError ?: "Unknown error",
                    modifier = Modifier.fillMaxWidth()
                )

                // Телефон
                UIKitInput(
                    value = uiState.phone,
                    onValueChange = onPhoneChange,
                    hint = "+7 000 000-00-00",
                    isError = uiState.phoneError != null,
                    errorMessage = uiState.phoneError ?: "Unknown error",
                    modifier = Modifier.fillMaxWidth()
                )

                // Город
                UIKitInput(
                    value = uiState.city,
                    onValueChange = onCityChange,
                    hint = "Город",
                    modifier = Modifier.fillMaxWidth()
                )

                // О себе
                UIKitInput(
                    value = uiState.description,
                    onValueChange = onDescriptionChange,
                    hint = "Расскажите о себе",
                    isError = uiState.descriptionError != null,
                    errorMessage = uiState.descriptionError ?: "Unknown error",
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        }

        // 3. Интересы
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpacingTokens.L),
                verticalArrangement = Arrangement.spacedBy(SpacingTokens.S)
            ) {
                TextHeading2(text = "Интересы")

                // Теги (кликабельные для удаления)
                if (uiState.interests.isNotEmpty()) {
                    UIKitTagGroup(
                        tags = uiState.interests,
                        size = UIKitTagSize.MEDIUM,
                        onTagClick = onRemoveInterest,  // Клик удаляет тег
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Кнопка "+ Добавить"
                Text(
                    text = "+ Добавить",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable { onAddInterest() }
                )
            }
        }

        // 4. Социальные сети (4 отдельных поля)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpacingTokens.L),
            ) {
                TextHeading2(text = "Социальные сети")

                SocialMediaField(
                    icon = "📱",  // Можно заменить на иконку
                    label = "Хабр",
                    value = uiState.socialMedias["habr"] ?: "",
                    onValueChange = { onSocialMediaChange("habr", it) },
                    placeholder = "Habr",
                )

                SocialMediaField(
                    icon = "✈️",
                    label = "Telegram",
                    value = uiState.socialMedias["telegram"] ?: "",
                    onValueChange = { onSocialMediaChange("telegram", it) },
                    placeholder = "Telegram"
                )

                SocialMediaField(
                    icon = "💼",
                    label = "LinkedIn",
                    value = uiState.socialMedias["linkedin"] ?: "",
                    onValueChange = { onSocialMediaChange("linkedin", it) },
                    placeholder = "LinkedIn"
                )

                SocialMediaField(
                    icon = "🐙",
                    label = "GitHub",
                    value = uiState.socialMedias["github"] ?: "",
                    onValueChange = { onSocialMediaChange("github", it) },
                    placeholder = "GitHub"
                )
            }
        }

        // 5. Настройки приватности
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpacingTokens.L),
                verticalArrangement = Arrangement.spacedBy(SpacingTokens.S)
            ) {
                UIKitToggleRow(
                    label = "Показывать мои сообщества",
                    checked = uiState.showCommunities,
                    onCheckedChange = { onToggleShowCommunities() }
                )

                UIKitToggleRow(
                    label = "Показывать мои встречи",
                    checked = uiState.showMeetings,
                    onCheckedChange = { onToggleShowMeetings() }
                )

                UIKitToggleRow(
                    label = "Включить уведомления",
                    checked = uiState.notificationsEnabled,
                    onCheckedChange = { onToggleNotifications() }
                )
            }
        }

        // 6. Удалить профиль
        item {
            Spacer(modifier = Modifier.height(SpacingTokens.L))
            Text(
                text = "Удалить профиль",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDeleteProfile() }
                    .padding(horizontal = SpacingTokens.L, vertical = SpacingTokens.S)
            )
        }

        // Нижний отступ
        item {
            Spacer(modifier = Modifier.height(SpacingTokens.XL))
        }
    }
}

@Composable
private fun SocialMediaField(
    icon: String,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpacingTokens.M)
    ) {
        // Иконка + Label
        Row(
            modifier = Modifier.weight(0.35f),
            horizontalArrangement = Arrangement.spacedBy(SpacingTokens.S),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.bodyLarge
            )
            TextBody2(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Поле ввода
        UIKitInput(
            value = value,
            onValueChange = onValueChange,
            hint = placeholder,
            modifier = Modifier.fillMaxWidth()
        )
    }
}


