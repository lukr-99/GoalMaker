package com.goalmaker.app.application.planning

import com.goalmaker.app.application.sync.Replica
import com.goalmaker.app.domain.sync.SyncRules
import com.goalmaker.app.domain.sync.SyncedTable
import com.goalmaker.app.domain.sync.SyncedTableCatalog
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * Tasks as the M1 lists need them: read from the replica, written through its outbox. Every write
 * asks for a sync. The methods block on disk, so callers run them off the main thread. M2 grows this
 * into the planning use cases (days, Plan tomorrow, repeats).
 */
class TaskList(
    catalog: SyncedTableCatalog,
    private val replica: Replica,
    private val ownerId: () -> String?,
    private val now: () -> Instant,
    private val requestSync: () -> Unit,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val table = catalog[TABLE]

    /** Open, not deleted, oldest first. */
    fun open(): List<TaskItem> = replica.all(TABLE)
        .filter { it.isNull(SyncedTable.DELETED_AT) && it.text("status") == "open" }
        .map(::toItem)
        .sortedBy(TaskItem::createdAt)

    /** [open], again after every change to the tasks table. Collect it off the main thread. */
    fun watchOpen(): Flow<List<TaskItem>> = replica.watch(TABLE).map { open() }

    fun add(title: String): TaskItem? {
        val trimmed = title.trim()
        val owner = ownerId()
        if (trimmed.isEmpty() || owner == null) return null

        val values = table.columns.associateTo(LinkedHashMap<String, JsonElement>()) { it.name to JsonNull }
        values[SyncedTable.ID] = JsonPrimitive(newId())
        values[SyncedTable.OWNER_ID] = JsonPrimitive(owner)
        values["title"] = JsonPrimitive(trimmed.take(MAX_TITLE))
        values["notes"] = JsonPrimitive("")
        values["top_priority"] = JsonPrimitive(false)
        values["status"] = JsonPrimitive("open")
        values["position"] = JsonPrimitive(0.0)
        values[SyncedTable.CREATED_AT] = JsonPrimitive(timestamp())
        values[SyncedTable.UPDATED_AT] = JsonPrimitive("")
        val row = JsonObject(values)
        replica.queue(TABLE, row)
        requestSync()
        return toItem(row)
    }

    fun setDone(id: String, done: Boolean) = change(id) { row ->
        row["status"] = JsonPrimitive(if (done) "done" else "open")
        row["completed_at"] = if (done) JsonPrimitive(timestamp()) else JsonNull
    }

    fun delete(id: String) = change(id) { row -> row[SyncedTable.DELETED_AT] = JsonPrimitive(timestamp()) }

    private fun change(id: String, edit: (MutableMap<String, JsonElement>) -> Unit) {
        val row = replica.get(TABLE, id) ?: return
        val values = LinkedHashMap(row)
        edit(values)
        replica.queue(TABLE, JsonObject(values))
        requestSync()
    }

    private fun timestamp(): String = SyncRules.format(now())

    private fun toItem(row: JsonObject) = TaskItem(
        id = row.text(SyncedTable.ID).orEmpty(),
        title = row.text("title").orEmpty(),
        state = when (row.text("status")) {
            "done" -> TaskState.DONE
            "dropped" -> TaskState.DROPPED
            else -> TaskState.OPEN
        },
        topPriority = (row["top_priority"] as? JsonPrimitive)?.booleanOrNull ?: false,
        createdAt = row.text(SyncedTable.CREATED_AT).orEmpty(),
    )

    private companion object {
        const val TABLE = "tasks"
        const val MAX_TITLE = 500

        fun JsonObject.isNull(name: String): Boolean = this[name].let { it == null || it == JsonNull }

        fun JsonObject.text(name: String): String? =
            (this[name] as? JsonPrimitive)?.takeUnless { it == JsonNull }?.content
    }
}
