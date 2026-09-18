package com.goalmaker.app.application.auth

/** The outcome of a sign-in step, with failures the UI can explain in plain words. */
sealed interface AuthResult {
    data object Success : AuthResult

    data object WrongOrExpiredCode : AuthResult

    data object TooManyRequests : AuthResult

    data object Offline : AuthResult

    data class Failed(val detail: String) : AuthResult
}
