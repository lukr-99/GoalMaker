package com.goalmaker.app.application.sync

/** What one sync run did. */
data class SyncReport(
    val pushed: Int,
    val rejected: Int,
    val pulled: Int,
    val offline: Boolean,
    val problem: String?,
)
