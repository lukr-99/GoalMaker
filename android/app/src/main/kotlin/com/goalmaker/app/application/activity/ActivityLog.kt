package com.goalmaker.app.application.activity

/**
 * The server's activity log (docs/activity.md). It isn't part of the replica, so it is read online;
 * both calls throw RemoteUnavailableException when the server can't be reached.
 */
interface ActivityLog {
    /** The latest changes, newest first. */
    suspend fun recent(limit: Int = 60): List<ActivityEntry>

    /** Puts the row back the way [entryId] found it; the result reaches the replica through sync. */
    suspend fun undo(entryId: Long): UndoOutcome
}
