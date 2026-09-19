package com.goalmaker.app.application.planning

import com.goalmaker.app.application.sync.Replica
import com.goalmaker.app.domain.planning.NameBasedUuid
import com.goalmaker.app.domain.sync.SyncedTable
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Which rituals ran on which planning day, from any device (docs/reminders.md). A run's id comes
 * from the owner, the ritual and the day, so recording it on both devices makes one row. The methods
 * block on disk, so callers run them off the main thread.
 */
class RitualRunList(
    private val replica: Replica,
    private val rows: NewRows,
    private val requestSync: () -> Unit,
) {
    /** The planning days on which [ritual] was done or skipped. */
    fun ran(ritual: String): Set<LocalDate> = replica.all(TABLE)
        .filter { it.text("ritual") == ritual && it[SyncedTable.DELETED_AT].let { value -> value == null || value == JsonNull } }
        .mapNotNull { it.text("day")?.let(LocalDate::parse) }
        .toSet()

    /** [ran], again after every change to the runs. Collect it off the main thread. */
    fun watch(ritual: String): Flow<Set<LocalDate>> = replica.watch(TABLE).map { ran(ritual) }

    /** Records that [ritual] was done (or, when [skipped], skipped) for planning [day]. */
    fun record(ritual: String, day: LocalDate, skipped: Boolean = false) {
        val owner = rows.owner() ?: return
        val id = idOf(owner, ritual, day)
        val outcome = JsonPrimitive(if (skipped) "skipped" else "done")
        val existing = replica.get(TABLE, id)
        val row = if (existing != null) {
            JsonObject(existing + mapOf("outcome" to outcome, SyncedTable.DELETED_AT to JsonNull))
        } else {
            rows.create(
                TABLE,
                mapOf(SyncedTable.ID to JsonPrimitive(id), "ritual" to JsonPrimitive(ritual), "day" to JsonPrimitive(day.toString()), "outcome" to outcome),
            ) ?: return
        }
        replica.queue(TABLE, row)
        requestSync()
    }

    companion object {
        const val PLAN_TOMORROW = "plan_tomorrow"
        private const val TABLE = "ritual_runs"
        private const val NAMESPACE = "b8c61b22-5e0c-4f0a-9d1e-6f4a2c7e3b91"

        /** The id every device gives [ritual]'s run on [day] for [owner] (contracts/vectors/reminders.json). */
        fun idOf(owner: String, ritual: String, day: LocalDate): String =
            NameBasedUuid.of(NAMESPACE, "${owner.lowercase(Locale.ROOT)}/$ritual/$day")

        private fun JsonObject.text(name: String): String? = (this[name] as? JsonPrimitive)?.takeUnless { it == JsonNull }?.content
    }
}
