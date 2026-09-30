package com.goalmaker.app.application.sync

/**
 * What one sync run did. [signedOut] when the server had no session to take (nobody signed in, or
 * the session ended): offline too, but only a sign-in helps, so nothing retries on its own.
 */
data class SyncReport(
    val pushed: Int,
    val rejected: Int,
    val pulled: Int,
    val offline: Boolean,
    val problem: String?,
    val signedOut: Boolean = false,
)
