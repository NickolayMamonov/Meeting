package dev.whysoezzy.uikit.error

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.whysoezzy.common.error.ErrorType
import dev.whysoezzy.uikit.R

/**
 * Локализованное сообщение для ErrorType (R-020).
 * Заменяет хардкод-строки из toUserMessage() в :core:network.
 */
@Composable
fun ErrorType.asUserMessage(): String =
    stringResource(
        when (this) {
            ErrorType.NoConnection -> R.string.error_network
            ErrorType.Unauthorized -> R.string.error_unauthorized
            ErrorType.Server -> R.string.error_server
            ErrorType.Unknown -> R.string.error_unknown
        },
    )
