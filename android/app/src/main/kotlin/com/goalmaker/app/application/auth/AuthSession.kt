package com.goalmaker.app.application.auth

/** Whether someone is signed in. [Loading] lasts until the stored session has been read. */
sealed interface AuthSession {
    data object Loading : AuthSession

    data object SignedOut : AuthSession

    data class SignedIn(val email: String) : AuthSession
}
