package com.goalmaker.app.data.sync

import com.goalmaker.app.application.sync.RemoteRejectedException
import com.goalmaker.app.application.sync.RemoteTables
import com.goalmaker.app.data.supabase.PostgrestHttp
import com.goalmaker.app.domain.sync.RowCursor
import com.goalmaker.app.domain.sync.SyncedTable
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * [RemoteTables] over PostgREST with the publishable key and the user's access token. Generic JSON
 * rows, so the synced-table contract drives everything.
 */
class PostgrestRemoteTables(private val postgrest: PostgrestHttp) : RemoteTables {
    override suspend fun upsert(table: String, row: JsonObject): JsonObject {
        val rows = rows(
            postgrest.send(HttpMethod.Post, table) {
                parameter("on_conflict", SyncedTable.ID)
                header("Prefer", "resolution=merge-duplicates,return=representation")
                contentType(ContentType.Application.Json)
                setBody(JsonArray(listOf(row)).toString())
            },
        )
        return rows.singleOrNull() as? JsonObject
            ?: throw RemoteRejectedException("The server stored no $table row for ${row[SyncedTable.ID]}.")
    }

    override suspend fun pull(table: String, from: String?, after: RowCursor?, limit: Int): List<JsonObject> =
        rows(
            postgrest.send(HttpMethod.Get, table) {
                parameter("select", "*")
                parameter("order", "updated_at.asc,id.asc")
                parameter("limit", limit)
                from?.let { parameter("updated_at", "gte.$it") }
                after?.let { parameter("or", "(updated_at.gt.${it.updatedAt},and(updated_at.eq.${it.updatedAt},id.gt.${it.id}))") }
            },
        ).filterIsInstance<JsonObject>()

    private fun rows(text: String): JsonArray = Json.parseToJsonElement(text) as? JsonArray ?: JsonArray(emptyList())
}
