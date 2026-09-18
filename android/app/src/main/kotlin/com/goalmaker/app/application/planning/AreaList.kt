package com.goalmaker.app.application.planning

import com.goalmaker.app.application.sync.Replica
import com.goalmaker.app.domain.sync.SyncedTable
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * The owner's areas, read from the replica and created through its outbox. The methods block on
 * disk, so callers run them off the main thread. M2-11 adds renaming, colors, emoji and order.
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

    /** [all], again after every change to the areas table. Collect it off the main thread. */
    fun watch(): Flow<List<AreaItem>> = replica.watch(TABLE).map { all() }

    /** The area with this name, ignoring case and surrounding spaces. */
    fun find(name: String): AreaItem? = key(name).let { wanted -> all().firstOrNull { key(it.name) == wanted } }

    /** The area with this name, created (with the next unused palette color) when there is none. */
    fun findOrCreate(name: String): AreaItem? = find(name) ?: create(name)

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

    private fun toItem(row: JsonObject) = AreaItem(
        id = row.text(SyncedTable.ID),
        name = row.text("name"),
        colorId = row.text("color"),
        emoji = (row["emoji"] as? JsonPrimitive)?.takeUnless { it == JsonNull }?.content,
    )

    private companion object {
        const val TABLE = "areas"
        const val MAX_NAME = 60

        fun key(name: String) = name.trim().lowercase(Locale.ROOT)

        fun JsonObject.text(name: String) = (this[name] as? JsonPrimitive)?.takeUnless { it == JsonNull }?.content.orEmpty()
    }
}
