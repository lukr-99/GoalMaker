package com.goalmaker.app.application.planning

import com.goalmaker.app.application.sync.Replica
import com.goalmaker.app.domain.sync.SyncedTable
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/**
 * The owner's reviews, read from the replica and changed through its outbox (docs/reviews.md). A
 * review's id comes from the owner, the kind and the period, so the same review written here and on
 * the PC is one row. The methods block on disk, so callers run them off the main thread.
 */
class ReviewList(
    private val replica: Replica,
    private val rows: NewRows,
    private val requestSync: () -> Unit,
) {
    /** Every review that isn't deleted, newest period first. */
    fun all(): List<ReviewItem> = replica.all(TABLE).map(::toItem).filterNot(ReviewItem::deleted)
        .sortedWith(compareByDescending<ReviewItem> { it.periodStart }.thenBy(ReviewItem::kind))

    fun find(kind: String, periodStart: LocalDate): ReviewItem? {
        val owner = rows.owner() ?: return null
        return replica.get(TABLE, ReviewRules.idOf(owner, kind, periodStart))?.let(::toItem)?.takeUnless(ReviewItem::deleted)
    }

    /** [all], again after every change. Collect it off the main thread. */
    fun watch(): Flow<List<ReviewItem>> = replica.watch(TABLE).map { all() }

    /** The review of that period, made when it is the first time anyone opens it. Null when nobody is signed in. */
    fun open(kind: String, periodStart: LocalDate): ReviewItem? {
        find(kind, periodStart)?.let { return it }
        val owner = rows.owner() ?: return null
        val row = rows.create(
            TABLE,
            mapOf(
                SyncedTable.ID to JsonPrimitive(ReviewRules.idOf(owner, kind, periodStart)),
                "kind" to JsonPrimitive(kind),
                "period_start" to JsonPrimitive(periodStart.toString()),
                "summary" to JsonPrimitive(""),
                "reflections" to JsonArray(emptyList()),
            ),
        ) ?: return null
        replica.queue(TABLE, row)
        requestSync()
        return toItem(row)
    }

    /** How the period felt, from 1 to 5; null clears it. */
    fun setMood(id: String, mood: Int?): Boolean = change(id) { values -> values["mood"] = rating(mood) }

    fun setEnergy(id: String, energy: Int?): Boolean = change(id) { values -> values["energy"] = rating(energy) }

    /** The answers written, in the order they were asked; blank answers are kept so the prompt stays put. */
    fun setReflections(id: String, reflections: List<Reflection>): Boolean = change(id) { values ->
        values["reflections"] = JsonArray(
            reflections.take(MAX_REFLECTIONS).map { reflection ->
                JsonObject(
                    mapOf(
                        "prompt" to JsonPrimitive(reflection.promptId.take(MAX_PROMPT_ID)),
                        "answer" to JsonPrimitive(reflection.answer.take(MAX_ANSWER)),
                    ),
                )
            },
        )
    }

    /** The summary a Claude routine wrote, or the owner's own closing words. */
    fun setSummary(id: String, summary: String): Boolean = change(id) { values ->
        values["summary"] = JsonPrimitive(summary.take(MAX_SUMMARY))
    }

    fun delete(id: String): Boolean = change(id) { values -> values[SyncedTable.DELETED_AT] = JsonPrimitive(rows.timestamp()) }

    private fun rating(value: Int?): JsonElement = value?.takeIf { it in 1..5 }?.let(::JsonPrimitive) ?: JsonNull

    private fun change(id: String, edit: (MutableMap<String, JsonElement>) -> Unit): Boolean {
        val row = replica.get(TABLE, id)?.takeIf { it[SyncedTable.DELETED_AT].let { value -> value == null || value == JsonNull } }
            ?: return false
        val values = LinkedHashMap(row)
        edit(values)
        replica.queue(TABLE, JsonObject(values))
        requestSync()
        return true
    }

    private fun toItem(row: JsonObject) = ReviewItem(
        id = row.text(SyncedTable.ID).orEmpty(),
        kind = row.text("kind") ?: ReviewRules.WEEKLY,
        periodStart = row.text("period_start")?.let(LocalDate::parse) ?: LocalDate.EPOCH,
        mood = (row["mood"] as? JsonPrimitive)?.intOrNull,
        energy = (row["energy"] as? JsonPrimitive)?.intOrNull,
        summary = row.text("summary").orEmpty(),
        reflections = (row["reflections"] as? JsonArray).orEmpty().mapNotNull { element ->
            val reflection = element as? JsonObject ?: return@mapNotNull null
            val prompt = reflection.text("prompt") ?: return@mapNotNull null
            Reflection(prompt, reflection.text("answer").orEmpty())
        },
        deleted = row.text(SyncedTable.DELETED_AT) != null,
    )

    private companion object {
        const val TABLE = "reviews"
        const val MAX_REFLECTIONS = 20
        const val MAX_PROMPT_ID = 60
        const val MAX_ANSWER = 4000
        const val MAX_SUMMARY = 20000

        fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()

        fun JsonObject.text(name: String): String? = (this[name] as? JsonPrimitive)?.takeUnless { it == JsonNull }?.content
    }
}
