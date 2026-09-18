package com.goalmaker.app.application.sync

import com.goalmaker.app.domain.sync.OutboxEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

/**
 * The device's copy of the synced tables, its outbox and watermarks (docs/sync.md). Rows are JSON
 * objects in wire shape: booleans are JSON booleans, timestamps normalized UTC text. The methods
 * block on disk, so callers run them off the main thread.
 */
interface Replica {
    /** The table's change counter: the current value first, then a new one after each commit that changed it. */
    fun watch(table: String): Flow<Long>

    fun get(table: String, id: String): JsonObject?

    fun all(table: String): List<JsonObject>

    /** A local write: stores the row and queues it for the push, in one transaction. */
    fun queue(table: String, row: JsonObject)

    fun isPending(table: String, id: String): Boolean

    fun pendingCount(): Int

    fun outbox(): List<OutboxEntry>

    /** Stores the server's copy and removes the entry, unless the row changed again meanwhile. */
    fun completePush(entry: OutboxEntry, serverRow: JsonObject)

    fun failPush(entry: OutboxEntry, error: String)

    /** Stores a row from the server as it is. */
    fun put(table: String, row: JsonObject)

    fun dropPending(table: String, id: String)

    fun watermark(table: String): String?

    fun setWatermark(table: String, watermark: String)

    /** Removes the table's rows that have no pending change, and its watermark (full resync). */
    fun clearSynced(table: String)

    /** Empties every synced table, the outbox and the watermarks (sign-out). */
    fun clearAll()

    fun <T> inTransaction(work: () -> T): T
}
