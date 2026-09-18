package com.goalmaker.app.application.sync

import com.goalmaker.app.domain.sync.RowCursor
import kotlinx.serialization.json.JsonObject

/**
 * The server's synced tables (PostgREST in the app, a fake in tests). Throws
 * [RemoteUnavailableException] when the server can't be reached and [RemoteRejectedException] when
 * it refuses one row.
 */
interface RemoteTables {
    /** Inserts or updates by id and returns the row as the server stored it. */
    suspend fun upsert(table: String, row: JsonObject): JsonObject

    /** Rows with updated_at at or after [from] (all when null), after [after], in (updated_at, id) order. */
    suspend fun pull(table: String, from: String?, after: RowCursor?, limit: Int): List<JsonObject>
}
