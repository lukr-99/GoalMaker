package com.goalmaker.app.application.planning

import com.goalmaker.app.application.sync.Replica
import com.goalmaker.app.domain.sync.SyncedTable
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * The owner's areas, read from the replica and created through its outbox. The methods block on
 * disk, so callers run them off the main thread. M2-11 adds renaming, colors, emoji, order and
 * archiving: an archived area keeps its tasks but leaves the pickers and filters, and naming it
 * again (the composer's @Area) brings it back.
 */
class AreaList(
    private val replica: Replica,
    private val rows: NewRows,
    /** The area palette's color ids in order (themes.json), for new areas. */
    private val paletteIds: List<String>,
    private val requestSync: () -> Unit,
) {
    fun all(): List<AreaItem> = replica.all(TABLE)
        .filter { it[SyncedTable.DELETED_AT].let { value -> value == null || value == JsonNull } }
        .sortedWith(compareBy({ (it["position"] as? JsonPrimitive)?.doubleOrNull ?: 0.0 }, { it.text("name").lowercase(Locale.ROOT) }))
        .map(::toItem)

    /** The areas in use, for pickers and filters: [all] without the archived ones. */
    fun active(): List<AreaItem> = all().filterNot(AreaItem::archived)

    /** [all], again after every change to the areas table. Collect it off the main thread. */
    fun watch(): Flow<List<AreaItem>> = replica.watch(TABLE).map { all() }

    /** The area with this name, ignoring case and surrounding spaces. */
    fun find(name: String): AreaItem? = key(name).let { wanted -> all().firstOrNull { key(it.name) == wanted } }

    /** The area with this name, brought back if archived, or created (with the next unused palette color) when there is none. */
    fun findOrCreate(name: String): AreaItem? {
        val found = find(name) ?: return create(name)
        if (!found.archived) return found
        restore(found.id)
        return found.copy(archived = false)
    }

    fun create(name: String): AreaItem? {
        val trimmed = name.trim().take(MAX_NAME)
        if (trimmed.isEmpty()) return null
        val existing = all()
        val used = existing.map { it.colorId }.toSet()
        val color = paletteIds.firstOrNull { it !in used } ?: paletteIds[existing.size % paletteIds.size]
        val row = rows.create(
            TABLE,
            mapOf(
                "name" to JsonPrimitive(trimmed),
                "color" to JsonPrimitive(color),
                "position" to JsonPrimitive(existing.size.toDouble()),
            ),
        ) ?: return null
        replica.queue(TABLE, row)
        requestSync()
        return toItem(row)
    }

    /** The palette's color ids in order, for the color picker. */
    fun palette(): List<String> = paletteIds

    /** Renames an area. False when the name is blank or another area already has it (ignoring case). */
    fun rename(id: String, name: String): Boolean {
        val trimmed = name.trim().take(MAX_NAME)
        val clash = find(trimmed)
        if (trimmed.isEmpty() || (clash != null && clash.id != id)) return false
        return change(id) { it["name"] = JsonPrimitive(trimmed) }
    }

    /** Gives an area another color from the palette. False for a color the palette doesn't have. */
    fun recolor(id: String, colorId: String): Boolean =
        colorId in paletteIds && change(id) { it["color"] = JsonPrimitive(colorId) }

    /** Sets the area's emoji, or takes it away when [emoji] is blank. */
    fun setEmoji(id: String, emoji: String?): Boolean {
        val trimmed = emoji?.trim()?.takeIf(String::isNotEmpty)?.take(MAX_EMOJI)
        return change(id) { it["emoji"] = trimmed?.let(::JsonPrimitive) ?: JsonNull }
    }

    /** Hides an area from the pickers and filters; its tasks keep it. */
    fun archive(id: String): Boolean = change(id) { it[ARCHIVED_AT] = JsonPrimitive(rows.timestamp()) }

    /** Brings an archived area back into the pickers and filters. */
    fun restore(id: String): Boolean = change(id) { it[ARCHIVED_AT] = JsonNull }

    /**
     * Moves an area in use to [index] among the areas in use, the order the pickers use, and numbers
     * them all again; archived areas keep their order after them.
     */
    fun move(id: String, index: Int) {
        val (archived, inUse) = all().partition(AreaItem::archived)
        val order = inUse.toMutableList()
        val area = order.firstOrNull { it.id == id } ?: return
        order.remove(area)
        order.add(index.coerceIn(0, order.size), area)
        order += archived
        replica.inTransaction {
            order.forEachIndexed { position, item ->
                replica.get(TABLE, item.id)?.let { row ->
                    replica.queue(TABLE, JsonObject(row + ("position" to JsonPrimitive(position.toDouble()))))
                }
            }
        }
        requestSync()
    }

    /** Deletes an area. Its tasks stay and lose the area, so one without a day goes back to the Inbox. */
    fun delete(id: String) {
        val stamp = JsonPrimitive(rows.timestamp())
        replica.inTransaction {
            val row = replica.get(TABLE, id) ?: return@inTransaction
            replica.queue(TABLE, JsonObject(row + (SyncedTable.DELETED_AT to stamp)))
            replica.all(TASKS).filter { it.text("area_id") == id }.forEach { task ->
                replica.queue(TASKS, JsonObject(task + ("area_id" to JsonNull)))
            }
        }
        requestSync()
    }

    private fun change(id: String, edit: (MutableMap<String, JsonElement>) -> Unit): Boolean {
        val row = replica.get(TABLE, id)?.takeIf { it[SyncedTable.DELETED_AT].let { value -> value == null || value == JsonNull } }
            ?: return false
        val values = LinkedHashMap(row)
        edit(values)
        replica.queue(TABLE, JsonObject(values))
        requestSync()
        return true
    }

    private fun toItem(row: JsonObject) = AreaItem(
        id = row.text(SyncedTable.ID),
        name = row.text("name"),
        colorId = row.text("color"),
        emoji = (row["emoji"] as? JsonPrimitive)?.takeUnless { it == JsonNull }?.content,
        archived = row[ARCHIVED_AT].let { it != null && it != JsonNull },
    )

    private companion object {
        const val TABLE = "areas"
        const val TASKS = "tasks"
        const val ARCHIVED_AT = "archived_at"
        const val MAX_NAME = 60
        const val MAX_EMOJI = 16

        fun key(name: String) = name.trim().lowercase(Locale.ROOT)

        fun JsonObject.text(name: String) = (this[name] as? JsonPrimitive)?.takeUnless { it == JsonNull }?.content.orEmpty()
    }
}
