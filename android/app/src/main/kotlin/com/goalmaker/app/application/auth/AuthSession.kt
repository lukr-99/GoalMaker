package com.goalmaker.app.application.auth

/**
 * Whether someone is signed in. [Loading] lasts until the stored session has been read. [SignedIn]
 * carries the account's id, which owns every row the replica holds.
 */
sealed interface AuthSession {
    data object Loading : AuthSession

    data object SignedOut : AuthSession

    data class SignedIn(val userId: String, val email: String) : AuthSession
}
