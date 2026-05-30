package dev.whysoezzy.auth.presentation.name

sealed interface NameFieldError {
    data object Blank : NameFieldError
    data object TooShort : NameFieldError
    data object NonLetter : NameFieldError
    data class Remote(val message: String) : NameFieldError
}