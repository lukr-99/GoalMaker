package com.goalmaker.app.data.sync

import com.goalmaker.app.application.sync.RemoteTables
import com.goalmaker.app.domain.sync.RowCursor
import com.goalmaker.app.domain.sync.SyncedTable
import java.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A dev build's server: it keeps nothing and has nothing new, so every change counts as pushed and the
 * replica is the only copy (docs/sync.md). It stamps updated_at as the real server would.
 */
class LocalOnlyRemoteTables(private val now: () -> Instant) : RemoteTables {
    override suspend fun upsert(table: String, row: JsonObject): JsonObject =
        JsonObject(row + (SyncedTable.UPDATED_AT to JsonPrimitive(now().toString())))

    override suspend fun pull(table: String, from: String?, after: RowCursor?, limit: Int): List<JsonObject> = emptyList()
}
