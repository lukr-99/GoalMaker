package com.goalmaker.app.application.planning

import com.goalmaker.app.application.sync.Replica
import com.goalmaker.app.domain.sync.SyncedTable
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/**
 * The owner's life goals and their pictures' rows, read from the replica and changed through its
 * outbox (docs/life-goals.md). The picture files are not here (ADR 0018). The methods block on disk,
 * so callers run them off the main thread.
 */
class LifeGoalList(
    private val replica: Replica,
    private val rows: NewRows,
    private val requestSync: () -> Unit,
) {
    /** Every life goal that isn't deleted, in the place's order ([LifeGoalRules.ordered]). */
    fun all(): List<LifeGoalItem> = LifeGoalRules.ordered(live(TABLE).map(::toItem))

    fun get(id: String): LifeGoalItem? = replica.get(TABLE, id)?.let(::toItem)?.takeUnless(LifeGoalItem::deleted)

    /** [all], again after every change to a life goal. Collect it off the main thread. */
    fun watch(): Flow<List<LifeGoalItem>> = replica.watch(TABLE).map { all() }

    /** The pictures of [lifeGoalId] that aren't deleted, in their order. */
    fun pictures(lifeGoalId: String): List<LifeGoalPicture> = allPictures().filter { it.lifeGoalId == lifeGoalId }

    /** Every picture that isn't deleted, by life goal and then in its order. */
    fun allPictures(): List<LifeGoalPicture> = live(PICTURES).map(::toPicture)
        .sortedWith(compareBy<LifeGoalPicture> { it.lifeGoalId }.thenBy { it.position }.thenBy { it.id })

    /** [allPictures], again after every change to a picture. */
    fun watchPictures(): Flow<List<LifeGoalPicture>> = replica.watch(PICTURES).map { allPictures() }

    /** Adds an open life goal after the others. Null without a title or a why. */
    fun add(draft: LifeGoalDraft): LifeGoalItem? {
        val clean = check(draft) ?: return null
        val last = live(TABLE).maxOfOrNull { it.number("position") ?: 0.0 }
        val row = rows.create(
            TABLE,
            values(clean) + mapOf(
                "status" to JsonPrimitive(LifeGoalRules.OPEN),
                "position" to JsonPrimitive(last?.plus(1) ?: 0.0),
                "made_by" to JsonPrimitive(ProjectRules.OWNER),
            ),
        ) ?: return null
        queue(TABLE, row)
        return toItem(row)
    }

    fun update(id: String, draft: LifeGoalDraft): Boolean {
        val clean = check(draft) ?: return false
        return change(TABLE, id) { values -> values.putAll(values(clean)) }
    }

    fun achieve(id: String): Boolean = close(id, LifeGoalRules.ACHIEVED)

    fun drop(id: String): Boolean = close(id, LifeGoalRules.DROPPED)

    fun reopen(id: String): Boolean = change(TABLE, id) { values ->
        values["status"] = JsonPrimitive(LifeGoalRules.OPEN)
        values["closed_at"] = JsonNull
    }

    /** Puts the open life goals in the order of [ids]; a life goal not named keeps its place after them. */
    fun reorder(ids: List<String>): Boolean {
        val byId = live(TABLE).associateBy { it.text(SyncedTable.ID) }
        if (ids.any { it !in byId }) return false
        ids.forEachIndexed { index, id ->
            val row = byId.getValue(id)
            if (row.number("position") != index.toDouble()) {
                replica.queue(TABLE, JsonObject(LinkedHashMap(row).apply { put("position", JsonPrimitive(index.toDouble())) }))
            }
        }
        requestSync()
        return true
    }

    /** Deletes a life goal with its pictures' rows, all with one time so [restore] can bring them back together. */
    fun delete(id: String): Boolean {
        val row = live(TABLE).firstOrNull { it.text(SyncedTable.ID) == id } ?: return false
        val at = JsonPrimitive(rows.timestamp())
        live(PICTURES).filter { it.text("life_goal_id") == id }.forEach { picture ->
            replica.queue(PICTURES, JsonObject(LinkedHashMap(picture).apply { put(SyncedTable.DELETED_AT, at) }))
        }
        queue(TABLE, JsonObject(LinkedHashMap(row).apply { put(SyncedTable.DELETED_AT, at) }))
        return true
    }

    /** Brings a deleted life goal back with the pictures deleted along with it, as the undo after a delete does. */
    fun restore(id: String): Boolean {
        val row = replica.get(TABLE, id) ?: return false
        val deletedAt = row.text(SyncedTable.DELETED_AT) ?: return false
        replica.all(PICTURES)
            .filter { it.text("life_goal_id") == id && it.text(SyncedTable.DELETED_AT) == deletedAt }
            .forEach { picture ->
                replica.queue(PICTURES, JsonObject(LinkedHashMap(picture).apply { put(SyncedTable.DELETED_AT, JsonNull) }))
            }
        queue(TABLE, JsonObject(LinkedHashMap(row).apply { put(SyncedTable.DELETED_AT, JsonNull) }))
        return true
    }

    /**
     * Adds a picture's row after the life goal's others, for a file of [width] by [height] pixels; the
     * caller keeps the file under the returned id. Null when the life goal is gone or the size is not real.
     */
    fun addPicture(lifeGoalId: String, width: Int, height: Int): LifeGoalPicture? {
        if (get(lifeGoalId) == null || width !in 1..MAX_SIDE || height !in 1..MAX_SIDE) return null
        val last = pictures(lifeGoalId).maxOfOrNull { it.position }
        val row = rows.create(
            PICTURES,
            mapOf(
                "life_goal_id" to JsonPrimitive(lifeGoalId),
                "position" to JsonPrimitive(last?.plus(1) ?: 0.0),
                "width" to JsonPrimitive(width),
                "height" to JsonPrimitive(height),
            ),
        ) ?: return null
        queue(PICTURES, row)
        return toPicture(row)
    }

    fun removePicture(id: String): Boolean =
        change(PICTURES, id) { values -> values[SyncedTable.DELETED_AT] = JsonPrimitive(rows.timestamp()) }

    /** Puts [lifeGoalId]'s pictures in the order of [ids]. */
    fun reorderPictures(lifeGoalId: String, ids: List<String>): Boolean {
        val byId = live(PICTURES).filter { it.text("life_goal_id") == lifeGoalId }.associateBy { it.text(SyncedTable.ID) }
        if (ids.any { it !in byId }) return false
        ids.forEachIndexed { index, id ->
            val row = byId.getValue(id)
            if (row.number("position") != index.toDouble()) {
                replica.queue(PICTURES, JsonObject(LinkedHashMap(row).apply { put("position", JsonPrimitive(index.toDouble())) }))
            }
        }
        requestSync()
        return true
    }

    private fun close(id: String, status: String): Boolean = change(TABLE, id) { values ->
        values["status"] = JsonPrimitive(status)
        values["closed_at"] = JsonPrimitive(rows.timestamp())
    }

    private fun check(draft: LifeGoalDraft): LifeGoalDraft? {
        val title = draft.title.trim().take(MAX_TITLE)
        val why = draft.why.trim().take(MAX_WHY)
        if (title.isEmpty() || why.isEmpty()) return null
        return draft.copy(title = title, why = why)
    }

    private fun values(draft: LifeGoalDraft): Map<String, JsonElement> = mapOf(
        "title" to JsonPrimitive(draft.title),
        "why" to JsonPrimitive(draft.why),
        "by_date" to (draft.by?.let { JsonPrimitive(it.toString()) } ?: JsonNull),
        "area_id" to (draft.areaId?.let(::JsonPrimitive) ?: JsonNull),
    )

    private fun live(table: String): List<JsonObject> =
        replica.all(table).filter { it.text(SyncedTable.DELETED_AT) == null }

    private fun change(table: String, id: String, edit: (MutableMap<String, JsonElement>) -> Unit): Boolean {
        val row = replica.get(table, id)?.takeIf { it.text(SyncedTable.DELETED_AT) == null } ?: return false
        val values = LinkedHashMap(row)
        edit(values)
        queue(table, JsonObject(values))
        return true
    }

    private fun queue(table: String, row: JsonObject) {
        replica.queue(table, row)
        requestSync()
    }

    private fun JsonObject.number(name: String) = (this[name] as? JsonPrimitive)?.doubleOrNull

    private fun toItem(row: JsonObject) = LifeGoalItem(
        id = row.text(SyncedTable.ID).orEmpty(),
        title = row.text("title").orEmpty(),
        why = row.text("why").orEmpty(),
        by = row.text("by_date")?.let(LocalDate::parse),
        areaId = row.text("area_id"),
        status = row.text("status") ?: LifeGoalRules.OPEN,
        closedAt = row.text("closed_at"),
        position = row.number("position") ?: 0.0,
        createdAt = row.text(SyncedTable.CREATED_AT).orEmpty(),
        madeBy = row.text("made_by") ?: ProjectRules.OWNER,
        deleted = row.text(SyncedTable.DELETED_AT) != null,
    )

    private fun toPicture(row: JsonObject) = LifeGoalPicture(
        id = row.text(SyncedTable.ID).orEmpty(),
        lifeGoalId = row.text("life_goal_id").orEmpty(),
        width = (row["width"] as? JsonPrimitive)?.intOrNull ?: 0,
        height = (row["height"] as? JsonPrimitive)?.intOrNull ?: 0,
        position = row.number("position") ?: 0.0,
        deleted = row.text(SyncedTable.DELETED_AT) != null,
    )

    private companion object {
        const val TABLE = "life_goals"
        const val PICTURES = "life_goal_pictures"
        const val MAX_TITLE = 200
        const val MAX_WHY = 2000
        const val MAX_SIDE = 4096

        fun JsonObject.text(name: String): String? = (this[name] as? JsonPrimitive)?.takeUnless { it == JsonNull }?.content
    }
}
