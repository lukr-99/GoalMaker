package com.goalmaker.app.application.planning

import com.goalmaker.app.domain.sync.SyncRules
import com.goalmaker.app.domain.sync.SyncedTable
import com.goalmaker.app.domain.sync.SyncedTableCatalog
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Complete rows for a local insert: every described column, a new id, the signed-in owner, the
 * creation time, and an empty updated_at for the server to stamp (docs/sync.md).
 */
class NewRows(
    private val catalog: SyncedTableCatalog,
    private val ownerId: () -> String?,
    private val now: () -> Instant,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    /** The owner rows are written for, or null when nobody is signed in. */
    fun owner(): String? = ownerId()

    fun timestamp(): String = SyncRules.format(now())

    /**
     * A new row of [table] with [values] set, or null when nobody is signed in. A column the server
     * needs a value in must have one: a null there would be refused on the push, so it fails here at
     * the write, where the test that covers the write can see it.
     */
    fun create(table: String, values: Map<String, JsonElement>): JsonObject? {
        val owner = ownerId() ?: return null
        val described = catalog[table]
        val row = described.columns.associateTo(LinkedHashMap<String, JsonElement>()) { it.name to JsonNull }
        row[SyncedTable.ID] = JsonPrimitive(newId())
        row[SyncedTable.OWNER_ID] = JsonPrimitive(owner)
        row[SyncedTable.CREATED_AT] = JsonPrimitive(timestamp())
        row[SyncedTable.UPDATED_AT] = JsonPrimitive("")
        row.putAll(values)
        val missing = described.required.filter { column -> row[column].let { it == null || it == JsonNull } }
        check(missing.isEmpty()) { "a new $table row needs a value in ${missing.joinToString()}" }
        return JsonObject(row)
    }
}
