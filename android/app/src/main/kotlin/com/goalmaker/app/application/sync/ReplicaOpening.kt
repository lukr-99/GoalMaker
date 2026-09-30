package com.goalmaker.app.application.sync

/**
 * Whether the replica opened at start (M6-06). [Opening] lasts until the first try is done; nothing
 * reads the replica before [Open].
 */
sealed interface ReplicaOpening {
    data object Opening : ReplicaOpening

    data object Open : ReplicaOpening

    /** It would not open. [newerApp] when a newer GoalMaker wrote it, so updating is the way on. */
    data class Failed(val newerApp: Boolean) : ReplicaOpening
}
