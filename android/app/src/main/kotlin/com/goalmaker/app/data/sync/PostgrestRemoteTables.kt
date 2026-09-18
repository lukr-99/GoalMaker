package com.goalmaker.app.data.sync

import com.goalmaker.app.application.sync.RemoteRejectedException
import com.goalmaker.app.application.sync.RemoteTables
import com.goalmaker.app.application.sync.RemoteUnavailableException
import com.goalmaker.app.domain.sync.RowCursor
import com.goalmaker.app.domain.sync.SyncedTable
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * [RemoteTables] over PostgREST with the publishable key and the user's access token. Generic JSON
 * rows, so the synced-table contract drives everything.
 */
class PostgrestRemoteTables(
    private val http: HttpClient,
    baseUrl: String,
    private val publishableKey: String,
    private val accessToken: suspend () -> String?,
) : RemoteTables {
    private val restUrl = baseUrl.trimEnd('/') + "/rest/v1/"

    override suspend fun upsert(table: String, row: JsonObject): JsonObject {
        val rows = send(HttpMethod.Post, table) {
            parameter("on_conflict", SyncedTable.ID)
            header("Prefer", "resolution=merge-duplicates,return=representation")
            contentType(ContentType.Application.Json)
            setBody(JsonArray(listOf(row)).toString())
        }
        return rows.singleOrNull() as? JsonObject
            ?: throw RemoteRejectedException("The server stored no $table row for ${row[SyncedTable.ID]}.")
    }

    override suspend fun pull(table: String, from: String?, after: RowCursor?, limit: Int): List<JsonObject> =
        send(HttpMethod.Get, table) {
            parameter("select", "*")
            parameter("order", "updated_at.asc,id.asc")
            parameter("limit", limit)
            from?.let { parameter("updated_at", "gte.$it") }
            after?.let { parameter("or", "(updated_at.gt.${it.updatedAt},and(updated_at.eq.${it.updatedAt},id.gt.${it.id}))") }
        }.filterIsInstance<JsonObject>()

    private suspend fun send(method: HttpMethod, table: String, configure: HttpRequestBuilder.() -> Unit): JsonArray {
        val token = accessToken() ?: throw RemoteUnavailableException("Not signed in.")
        val response: HttpResponse
        val text: String
        try {
            response = http.request(restUrl + table) {
                this.method = method
                header("apikey", publishableKey)
                bearerAuth(token)
                accept(ContentType.Application.Json)
                configure()
            }
            text = response.bodyAsText()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: IOException) {
            // Includes Ktor's timeouts, which are IOExceptions too.
            throw RemoteUnavailableException("The server can't be reached.", error)
        }

        if (response.status.isSuccess()) {
            return Json.parseToJsonElement(text) as? JsonArray ?: JsonArray(emptyList())
        }

        val status = response.status
        val temporary = status == HttpStatusCode.Unauthorized || status == HttpStatusCode.RequestTimeout ||
            status == HttpStatusCode.TooManyRequests || status.value >= 500
        val message = "HTTP ${status.value}: ${text.take(300)}"
        throw if (temporary) RemoteUnavailableException(message) else RemoteRejectedException(message)
    }
}
