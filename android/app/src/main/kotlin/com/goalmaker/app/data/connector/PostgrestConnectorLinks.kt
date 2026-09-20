package com.goalmaker.app.data.connector

import com.goalmaker.app.application.connector.ConnectorLink
import com.goalmaker.app.application.connector.ConnectorLinks
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

/** [ConnectorLinks] over PostgREST: `connector_links` and the functions that make and revoke links (0007). */
class PostgrestConnectorLinks(private val postgrest: PostgrestHttp) : ConnectorLinks {
    override suspend fun list(): List<ConnectorLink> {
        val text = postgrest.send(HttpMethod.Get, "connector_links") {
            parameter("select", "id,created_at,last_used_at,revoked_at")
            parameter("order", "created_at.desc")
        }
        // A row without an id is passed over rather than thrown on.
        return (Json.parseToJsonElement(text) as? JsonArray).orEmpty().filterIsInstance<JsonObject>().mapNotNull { row ->
            ConnectorLink(
                id = row.text("id") ?: return@mapNotNull null,
                createdAt = row.text("created_at")?.let(SyncRules::instantOf) ?: Instant.EPOCH,
                lastUsedAt = row.text("last_used_at")?.let(SyncRules::instantOf),
                revokedAt = row.text("revoked_at")?.let(SyncRules::instantOf),
            )
        }
    }

    override suspend fun create(): String {
        val text = postgrest.send(HttpMethod.Post, "rpc/create_connector_link") {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        // A function returning text answers with a JSON string.
        return (Json.parseToJsonElement(text) as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: throw RemoteRejectedException("The server returned no connector secret.")
    }

    override suspend fun revoke() {
        postgrest.send(HttpMethod.Post, "rpc/revoke_connector_links") {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
    }

    private fun JsonObject.text(name: String): String? = (this[name] as? JsonPrimitive)?.takeUnless { it == JsonNull }?.content
}
