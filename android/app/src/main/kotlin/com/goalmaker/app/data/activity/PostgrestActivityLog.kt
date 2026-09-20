package com.goalmaker.app.data.activity

import com.goalmaker.app.application.activity.ActivityEntry
import com.goalmaker.app.application.activity.ActivityLog
import com.goalmaker.app.application.activity.UndoOutcome
import com.goalmaker.app.application.sync.RemoteRejectedException
import com.goalmaker.app.data.supabase.PostgrestHttp
import com.goalmaker.app.domain.sync.SyncRules
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** [ActivityLog] over PostgREST: the `activity_log` table and the `undo_activity` function (0007). */
class PostgrestActivityLog(private val postgrest: PostgrestHttp) : ActivityLog {
    override suspend fun recent(limit: Int): List<ActivityEntry> {
        val text = postgrest.send(HttpMethod.Get, "activity_log") {
            parameter("select", "id,entity,entity_id,action,actor,before,after,created_at,undone_at")
            parameter("order", "id.desc")
            parameter("limit", limit)
        }
        // A row missing what an entry needs is passed over: a stranger's row never empties the screen.
        return (Json.parseToJsonElement(text) as? JsonArray).orEmpty().filterIsInstance<JsonObject>().mapNotNull { row ->
            ActivityEntry(
                id = (row["id"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null,
                entity = row.text("entity") ?: return@mapNotNull null,
                entityId = row.text("entity_id") ?: return@mapNotNull null,
                action = row.text("action") ?: return@mapNotNull null,
                actor = row.text("actor").orEmpty(),
                before = row["before"] as? JsonObject,
                after = row["after"] as? JsonObject ?: JsonObject(emptyMap()),
                createdAt = row.text("created_at")?.let(SyncRules::instantOf) ?: Instant.EPOCH,
                undoneAt = row.text("undone_at")?.let(SyncRules::instantOf),
            )
        }
    }

    override suspend fun undo(entryId: Long): UndoOutcome = try {
        postgrest.send(HttpMethod.Post, "rpc/undo_activity") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("entry_id", entryId) }.toString())
        }
        UndoOutcome.UNDONE
    } catch (refused: RemoteRejectedException) {
        when (refused.code) {
            "40001" -> UndoOutcome.CHANGED_SINCE
            "55000" -> UndoOutcome.ALREADY_UNDONE
            else -> UndoOutcome.NOT_POSSIBLE
        }
    }

    private fun JsonObject.text(name: String): String? = (this[name] as? JsonPrimitive)?.takeUnless { it == JsonNull }?.content
}
