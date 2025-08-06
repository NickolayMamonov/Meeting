package dev.whysoezzy.profile.edit.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import dev.whysoezzy.uikit.components.buttons.UIKitButton
import dev.whysoezzy.uikit.components.buttons.UIKitButtonState
import dev.whysoezzy.uikit.components.inputs.UIKitInput
import dev.whysoezzy.uikit.components.text.TextHeading1
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.SpacingTokens
import org.koin.androidx.compose.koinViewModel

@Composable
fun ProfileEditScreen(
    onBackPressed: () -> Unit,
    onSaveSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileEditViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onSaveSuccess()
        }
    }

    Scaffold { paddingValues ->
        when {
            uiState.isLoading && uiState.name.isEmpty() -> {
                LoadingContent(modifier = Modifier.padding(paddingValues))
            }

            else -> {
                EditContent(
                    uiState = uiState,
                    onNameChange = { viewModel.onEvent(ProfileEditEvent.UpdateName(it)) },
                    onSurnameChange = { viewModel.onEvent(ProfileEditEvent.UpdateSurname(it)) },
                    onEmailChange = { viewModel.onEvent(ProfileEditEvent.UpdateEmail(it)) },
                    onCityChange = { viewModel.onEvent(ProfileEditEvent.UpdateCity(it)) },
                    onDescriptionChange = { viewModel.onEvent(ProfileEditEvent.UpdateDescription(it)) },
                    onSaveClick = { viewModel.onEvent(ProfileEditEvent.Save) },
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
private fun EditContent(
    uiState: ProfileEditUiState,
    onNameChange: (String) -> Unit,
    onSurnameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onCityChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSaveClick: () -> Unit,
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(SpacingTokens.L),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.L)
    ) {
        TextHeading1(
            text = "Редактирование профиля",
            textAlign = TextAlign.Center
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
        ) {
            uiState.nameError?.let {
                UIKitInput(
                    value = uiState.name,
                    onValueChange = onNameChange,
                    isError = true,
                    errorMessage = it,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            uiState.surnameError?.let {
                UIKitInput(
                    value = uiState.surname,
                    onValueChange = onSurnameChange,
                    isError = true,
                    errorMessage = it,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            uiState.emailError?.let {
                UIKitInput(
                    value = uiState.email,
                    onValueChange = onEmailChange,
                    isError = true,
                    errorMessage = it,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            UIKitInput(
                value = uiState.city,
                onValueChange = onCityChange,
                modifier = Modifier.fillMaxWidth()
            )

            uiState.descriptionError?.let {
                UIKitInput(
                    value = uiState.description,
                    onValueChange = onDescriptionChange,
                    isError = true,
                    errorMessage = it,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (uiState.error != null) {
            Text(
                text = uiState.error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }

        UIKitButton(
            text = "Сохранить",
            onClick = onSaveClick,
            state = when {
                uiState.isSaving -> UIKitButtonState.LOADING
                uiState.isValid -> UIKitButtonState.PRIMARY
                else -> UIKitButtonState.DISABLED
            },
            modifier = Modifier.fillMaxWidth()
        )

        UIKitButton(
            text = "Отмена",
            onClick = onBackPressed,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Preview
@Composable
private fun ProfileEditScreenPreview() {
    UIKitTheme {
        EditContent(
            uiState = ProfileEditUiState(
                name = "Иван",
                surname = "Петров",
                email = "ivan.petrov@example.com",
                city = "Москва",
                description = "Android разработчик"
            ),
            onNameChange = { },
            onSurnameChange = { },
            onEmailChange = { },
            onCityChange = { },
            onDescriptionChange = { },
            onSaveClick = { },
            onBackPressed = { }
        )
    }
}