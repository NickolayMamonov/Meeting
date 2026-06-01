package dev.whysoezzy.auth.presentation.name

import com.whysoezzy.common.error.ErrorType

sealed interface NameFieldError {
    data object Blank : NameFieldError

    data object TooShort : NameFieldError

    data object NonLetter : NameFieldError

    data class Remote(
        val errorType: ErrorType,
    ) : NameFieldError
}
