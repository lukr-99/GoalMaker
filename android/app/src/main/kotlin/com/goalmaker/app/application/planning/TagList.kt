package com.goalmaker.app.application.planning

import com.goalmaker.app.application.sync.Replica
import com.goalmaker.app.domain.sync.SyncedTable
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The owner's tags by name, created through the outbox when the composer names a new one. */
class TagList(
    private val replica: Replica,
    private val rows: NewRows,
    private val requestSync: () -> Unit,
) {
    /** Tag ids by name, oldest first. */
    fun all(): List<Pair<String, String>> = replica.all(TABLE)
        .filter { it[SyncedTable.DELETED_AT].let { value -> value == null || value == JsonNull } }
        .sortedBy { it.text(SyncedTable.CREATED_AT) }
        .map { it.text(SyncedTable.ID) to it.text("name") }

    /** The tag names, again after every change. Collect it off the main thread. */
    fun watchNames(): Flow<List<String>> = replica.watch(TABLE).map { all().map { it.second } }

    /** The id of the tag with this name (ignoring case), created when there is none. */
    fun findOrCreate(name: String): String? {
        val trimmed = name.trim().take(MAX_NAME)
        if (trimmed.isEmpty()) return null
        val wanted = trimmed.lowercase(Locale.ROOT)
        all().firstOrNull { it.second.lowercase(Locale.ROOT) == wanted }?.let { return it.first }
        val row = rows.create(TABLE, mapOf("name" to JsonPrimitive(trimmed))) ?: return null
        replica.queue(TABLE, row)
        requestSync()
        return row.text(SyncedTable.ID)
    }

    private companion object {
        const val TABLE = "tags"
        const val MAX_NAME = 40

        fun JsonObject.text(name: String) = (this[name] as? JsonPrimitive)?.takeUnless { it == JsonNull }?.content.orEmpty()
    }
}
