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
    /** Every tag that isn't deleted, oldest first. */
    fun all(): List<TagItem> = replica.all(TABLE)
        .filter { it.isLive() }
        .sortedBy { it.text(SyncedTable.CREATED_AT) }
        .map { TagItem(it.text(SyncedTable.ID), it.text("name")) }

    /** [all], again after every change to the tags table. Collect it off the main thread. */
    fun watch(): Flow<List<TagItem>> = replica.watch(TABLE).map { all() }

    /** The tag names, again after every change. Collect it off the main thread. */
    fun watchNames(): Flow<List<String>> = watch().map { tags -> tags.map(TagItem::name) }

    /** The id of the tag with this name (ignoring case), created when there is none. */
    fun findOrCreate(name: String): String? {
        val trimmed = name.trim().take(MAX_NAME)
        if (trimmed.isEmpty()) return null
        find(trimmed)?.let { return it.id }
        val row = rows.create(TABLE, mapOf("name" to JsonPrimitive(trimmed))) ?: return null
        replica.queue(TABLE, row)
        requestSync()
        return row.text(SyncedTable.ID)
    }

    /** Renames a tag. False when the name is blank or another tag already has it (ignoring case). */
    fun rename(id: String, name: String): Boolean {
        val trimmed = name.trim().take(MAX_NAME)
        val clash = find(trimmed)
        if (trimmed.isEmpty() || (clash != null && clash.id != id)) return false
        val row = replica.get(TABLE, id)?.takeIf { it.isLive() } ?: return false
        replica.queue(TABLE, JsonObject(row + ("name" to JsonPrimitive(trimmed))))
        requestSync()
        return true
    }

    /** Deletes a tag and its links to tasks; the tasks stay. */
    fun delete(id: String) {
        val stamp = JsonPrimitive(rows.timestamp())
        replica.inTransaction {
            val row = replica.get(TABLE, id)?.takeIf { it.isLive() } ?: return@inTransaction
            replica.queue(TABLE, JsonObject(row + (SyncedTable.DELETED_AT to stamp)))
            replica.all(LINKS).filter { it.isLive() && it.text("tag_id") == id }.forEach { link ->
                replica.queue(LINKS, JsonObject(link + (SyncedTable.DELETED_AT to stamp)))
            }
        }
        requestSync()
    }

    /** Each task's tags: task id to the ids of the live tags linked to it, for the list filter. */
    fun links(): Map<String, Set<String>> {
        val live = all().map(TagItem::id).toSet()
        return replica.all(LINKS)
            .filter { it.isLive() && it.text("tag_id") in live }
            .groupBy({ it.text("task_id") }, { it.text("tag_id") })
            .mapValues { (_, ids) -> ids.toSet() }
    }

    /** [links], again after every change to the links. Collect it off the main thread. */
    fun watchLinks(): Flow<Map<String, Set<String>>> = replica.watch(LINKS).map { links() }

    private fun find(name: String): TagItem? {
        val wanted = name.trim().lowercase(Locale.ROOT)
        return all().firstOrNull { it.name.lowercase(Locale.ROOT) == wanted }
    }

    private companion object {
        const val TABLE = "tags"
        const val LINKS = "task_tags"
        const val MAX_NAME = 40

        fun JsonObject.isLive() = this[SyncedTable.DELETED_AT].let { value -> value == null || value == JsonNull }

        fun JsonObject.text(name: String) = (this[name] as? JsonPrimitive)?.takeUnless { it == JsonNull }?.content.orEmpty()
    }
}
