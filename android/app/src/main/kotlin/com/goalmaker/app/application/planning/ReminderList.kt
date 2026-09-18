package com.goalmaker.app.application.planning

import com.goalmaker.app.application.sync.Replica
import com.goalmaker.app.domain.sync.SyncRules
import com.goalmaker.app.domain.sync.SyncedTable
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Reminders as the device schedules them: read from the replica, written through its outbox
 * (docs/reminders.md). The table keeps instants, so this is where they become the device's local
 * times and back. The methods block on disk, so callers run them off the main thread.
 */
class ReminderList(
    private val replica: Replica,
    private val rows: NewRows,
    private val requestSync: () -> Unit,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    /** Every reminder that isn't deleted. */
    fun all(): List<ReminderItem> = replica.all(TABLE)
        .filter { it.isNull(SyncedTable.DELETED_AT) }
        .map(::toItem)

    /** [all], again after every change to the reminders table. Collect it off the main thread. */
    fun watchAll(): Flow<List<ReminderItem>> = replica.watch(TABLE).map { all() }

    /** The reminders on one task, soonest first. */
    fun forTask(taskId: String): List<ReminderItem> = all()
        .filter { it.taskId == taskId }
        .sortedWith(compareBy({ it.fireAt }, { it.offsetMinutes }, { it.id }))

    /** A reminder at its own time. Null when nobody is signed in. */
    fun addAt(taskId: String, at: LocalDateTime, important: Boolean = false): ReminderItem? =
        add(taskId, important, "fire_at" to JsonPrimitive(text(at)), "offset_minutes" to JsonNull)

    /** A reminder [minutes] before the task's planned time. Null when nobody is signed in. */
    fun addBefore(taskId: String, minutes: Int, important: Boolean = false): ReminderItem? =
        add(taskId, important, "fire_at" to JsonNull, "offset_minutes" to JsonPrimitive(-minutes))

    /** Handled from a notification: it never fires again, and the other device drops its copy. */
    fun markDone(id: String) = settle(id, "done")

    /** Swiped away: it never fires again. */
    fun dismiss(id: String) = settle(id, "dismissed")

    /** Comes back at [until] instead of its own time. */
    fun snooze(id: String, until: LocalDateTime) = change(id) { row ->
        row["state"] = JsonPrimitive("snoozed")
        row["snoozed_until"] = JsonPrimitive(text(until))
    }

    fun delete(id: String) = change(id) { row -> row[SyncedTable.DELETED_AT] = JsonPrimitive(rows.timestamp()) }

    private fun add(taskId: String, important: Boolean, vararg time: Pair<String, JsonElement>): ReminderItem? {
        if (rows.owner() == null) return null
        val row = rows.create(
            TABLE,
            mapOf(
                "task_id" to JsonPrimitive(taskId),
                "important" to JsonPrimitive(important),
                "state" to JsonPrimitive("pending"),
                "snoozed_until" to JsonNull,
            ) + time,
        ) ?: return null
        replica.queue(TABLE, row)
        requestSync()
        return toItem(row)
    }

    private fun settle(id: String, state: String) = change(id) { row ->
        row["state"] = JsonPrimitive(state)
        row["snoozed_until"] = JsonNull
    }

    private fun change(id: String, edit: (MutableMap<String, JsonElement>) -> Unit) {
        val row = replica.get(TABLE, id) ?: return
        val values = LinkedHashMap(row)
        edit(values)
        replica.queue(TABLE, JsonObject(values))
        requestSync()
    }

    private fun text(at: LocalDateTime): String = SyncRules.format(at.atZone(zone()).toInstant())

    private fun local(value: String?): LocalDateTime? =
        value?.let(SyncRules::instantOf)?.atZone(zone())?.toLocalDateTime()

    private fun toItem(row: JsonObject) = ReminderItem(
        id = row.text(SyncedTable.ID).orEmpty(),
        taskId = row.text("task_id").orEmpty(),
        state = when (row.text("state")) {
            "snoozed" -> ReminderState.SNOOZED
            "dismissed" -> ReminderState.DISMISSED
            "done" -> ReminderState.DONE
            else -> ReminderState.PENDING
        },
        important = (row["important"] as? JsonPrimitive)?.booleanOrNull ?: false,
        fireAt = local(row.text("fire_at")),
        offsetMinutes = (row["offset_minutes"] as? JsonPrimitive)?.intOrNull,
        snoozedUntil = local(row.text("snoozed_until")),
    )

    private companion object {
        const val TABLE = "reminders"

        fun JsonObject.isNull(name: String): Boolean = this[name].let { it == null || it == JsonNull }

        fun JsonObject.text(name: String): String? =
            (this[name] as? JsonPrimitive)?.takeUnless { it == JsonNull }?.content
    }
}
