package com.goalmaker.app.application.sync

import com.goalmaker.app.domain.sync.SyncedTable
import com.goalmaker.app.domain.sync.SyncedTableCatalog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Keeps the replica and its runs in step with who is signed in (docs/sign-in.md, docs/sync.md). A
 * replica only ever holds one account's rows. Signing in as the owner it holds picks the outbox up
 * where it was; signing in as anyone else empties it first, so one account's queued changes never
 * reach another. Signing out, or a session the server ended, stops the runs and leaves the replica
 * and its outbox as they are; only the sign-out in Settings empties it.
 */
class AccountSync(
    private val catalog: SyncedTableCatalog,
    private val replica: Replica,
    private val sync: SyncCoordinator,
    private val io: CoroutineDispatcher,
) {
    @Volatile private var readyFor: String? = null

    /**
     * Makes the replica [userId]'s before anything syncs, emptying it when it holds someone else's
     * rows. Cheap once done, so every path that syncs can call it.
     */
    suspend fun prepare(userId: String) {
        if (userId.isBlank() || readyFor == userId) return
        withContext(io) {
            if (owners().any { it != userId }) replica.clearAll()
        }
        readyFor = userId
    }

    /** Someone signed in: the replica becomes theirs, then a run pushes what waited and pulls. */
    suspend fun signedIn(userId: String) {
        prepare(userId)
        sync.request()
    }

    /** Nobody is signed in any more: no runs until someone is. The replica keeps what it has. */
    fun signedOut() {
        readyFor = null
        sync.cancelScheduled()
    }

    /**
     * True when the replica still holds an owner's rows while nobody is signed in. The sign-out in
     * Settings empties it, so this means the session ended without the owner asking.
     */
    suspend fun sessionEnded(): Boolean = withContext(io) { owners().any() }

    private fun owners(): Sequence<String> = catalog.tables.asSequence()
        .flatMap { table -> replica.all(table.name).asSequence() }
        .mapNotNull { row -> (row[SyncedTable.OWNER_ID] as? JsonPrimitive)?.contentOrNull }
}
