package com.goalmaker.app.data.supabase

import com.goalmaker.app.application.sync.RemoteRejectedException
import com.goalmaker.app.application.sync.RemoteUnavailableException
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Calls to PostgREST (`/rest/v1/...`) as the signed-in user: the publishable key and the user's
 * access token. A failure is RemoteUnavailableException when trying again later can help (offline,
 * a timeout, an expired token, the server busy) and RemoteRejectedException, with the database's
 * SQLSTATE when there is one, when it can't.
 */
class PostgrestHttp(
    private val http: HttpClient,
    baseUrl: String,
    private val publishableKey: String,
    private val accessToken: suspend () -> String?,
) {
    private val restUrl = baseUrl.trimEnd('/') + "/rest/v1/"

    /** Sends a request to [path] (a table, or `rpc/<function>`) and returns the body of a success. */
    suspend fun send(method: HttpMethod, path: String, configure: HttpRequestBuilder.() -> Unit = {}): String {
        val token = accessToken() ?: throw RemoteUnavailableException("Not signed in.")
        val response: HttpResponse
        val text: String
        try {
            response = http.request(restUrl + path) {
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
        if (response.status.isSuccess()) return text

        val status = response.status
        val temporary = status == HttpStatusCode.Unauthorized || status == HttpStatusCode.RequestTimeout ||
            status == HttpStatusCode.TooManyRequests || status.value >= 500
        val message = "HTTP ${status.value}: ${text.take(300)}"
        throw if (temporary) RemoteUnavailableException(message) else RemoteRejectedException(message, sqlState(text))
    }

    // PostgREST reports a database error as {"code": "40001", "message": ...}.
    private fun sqlState(body: String): String? = runCatching {
        ((Json.parseToJsonElement(body) as? JsonObject)?.get("code") as? JsonPrimitive)?.content
    }.getOrNull()
}
