package com.goalmaker.app.application.sync

import java.time.Instant

/** The device's sync state, the last success, and how many changes are still local. */
data class SyncStatus(
    val state: SyncState,
    val lastSyncedAt: Instant?,
    val pendingChanges: Int,
    val problem: String?,
) {
    companion object {
        val INITIAL = SyncStatus(SyncState.IDLE, lastSyncedAt = null, pendingChanges = 0, problem = null)
    }
}
