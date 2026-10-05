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

/**
 * The owner's calendar events (docs/calendar.md, ADR 0019), read from the replica and changed through
 * its outbox. A draft is held to the server's checks: a title of 1 to 200 characters, the last day not
 * before the first and at most 366 days after it, and notes up to 10,000 characters. The methods
 * block on disk, so callers run them off the main thread.
 */
class EventList(
    private val replica: Replica,
    private val rows: NewRows,
    private val requestSync: () -> Unit,
) {
    /** Every event that isn't deleted, in the day order ([EventRules.ORDER]). */
    fun all(): List<EventItem> = replica.all(TABLE).map(::toItem).filterNot(EventItem::deleted).sortedWith(EventRules.ORDER)

    fun get(id: String): EventItem? = replica.get(TABLE, id)?.let(::toItem)?.takeUnless(EventItem::deleted)

    /** The events with at least one day from [from] to [to], in the day order. */
    fun between(from: LocalDate, to: LocalDate): List<EventItem> =
        all().filter { !it.startsOn.isAfter(to) && !it.endsOn.isBefore(from) }

    /** [all], again after every change to an event. Collect it off the main thread. */
    fun watch(): Flow<List<EventItem>> = replica.watch(TABLE).map { all() }

    /** Adds an event made by the owner. Null when the draft is not one the server takes. */
    fun add(draft: EventDraft): EventItem? {
        val clean = check(draft) ?: return null
        val row = rows.create(TABLE, values(clean) + mapOf("made_by" to JsonPrimitive(ProjectRules.OWNER))) ?: return null
        queue(row)
        return toItem(row)
    }

    /** Changes an event's title, days, notes and area. False when it is gone or the draft is not valid. */
    fun update(id: String, draft: EventDraft): Boolean {
        val clean = check(draft) ?: return false
        return change(id) { values -> values.putAll(values(clean)) }
    }

    /** Deletes an event; [restore] brings it back. */
    fun delete(id: String): Boolean = change(id) { values -> values[SyncedTable.DELETED_AT] = JsonPrimitive(rows.timestamp()) }

    /** Brings a deleted event back, as the undo after a delete does. */
    fun restore(id: String): Boolean {
        val row = replica.get(TABLE, id)?.takeIf { it.text(SyncedTable.DELETED_AT) != null } ?: return false
        queue(JsonObject(LinkedHashMap(row).apply { put(SyncedTable.DELETED_AT, JsonNull) }))
        return true
    }

    private fun check(draft: EventDraft): EventDraft? {
        val title = draft.title.trim()
        val notes = draft.notes?.trim()?.takeIf(String::isNotEmpty)
        if (title.isEmpty() || title.length > EventRules.MAX_TITLE) return null
        if (notes != null && notes.length > EventRules.MAX_NOTES) return null
        if (!EventRules.validSpan(draft.startsOn, draft.endsOn)) return null
        return draft.copy(title = title, notes = notes)
    }

    private fun values(draft: EventDraft): Map<String, JsonElement> = mapOf(
        "title" to JsonPrimitive(draft.title),
        "starts_on" to JsonPrimitive(draft.startsOn.toString()),
        "ends_on" to JsonPrimitive(draft.endsOn.toString()),
        "notes" to (draft.notes?.let(::JsonPrimitive) ?: JsonNull),
        "area_id" to (draft.areaId?.let(::JsonPrimitive) ?: JsonNull),
    )

    private fun change(id: String, edit: (MutableMap<String, JsonElement>) -> Unit): Boolean {
        val row = replica.get(TABLE, id)?.takeIf { it.text(SyncedTable.DELETED_AT) == null } ?: return false
        val values = LinkedHashMap(row)
        edit(values)
        queue(JsonObject(values))
        return true
    }

    private fun queue(row: JsonObject) {
        replica.queue(TABLE, row)
        requestSync()
    }

    private fun toItem(row: JsonObject): EventItem {
        val startsOn = row.text("starts_on")?.let(LocalDate::parse) ?: LocalDate.MIN
        return EventItem(
            id = row.text(SyncedTable.ID).orEmpty(),
            title = row.text("title").orEmpty(),
            startsOn = startsOn,
            endsOn = row.text("ends_on")?.let(LocalDate::parse) ?: startsOn,
            notes = row.text("notes"),
            areaId = row.text("area_id"),
            madeBy = row.text("made_by") ?: ProjectRules.OWNER,
            createdAt = row.text(SyncedTable.CREATED_AT).orEmpty(),
            deleted = row.text(SyncedTable.DELETED_AT) != null,
        )
    }

    private companion object {
        const val TABLE = "events"

        fun JsonObject.text(name: String): String? = (this[name] as? JsonPrimitive)?.takeUnless { it == JsonNull }?.content
    }
}
