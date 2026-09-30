package com.goalmaker.app.data.supabase

import com.goalmaker.app.application.sync.NotSignedInException
import com.goalmaker.app.application.sync.RemoteRejectedException
import com.goalmaker.app.application.sync.RemoteUnavailableException
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.request
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
 * a timeout, the server busy) and RemoteRejectedException, with the database's SQLSTATE when there is
 * one, when it can't.
 *
 * A token the server turns away (401) gets one [refreshSession] and one more try. Turned away again,
 * the session is over: [endSession] signs the device out and the call fails with
 * NotSignedInException, which callers treat like offline, so nothing queued is lost (docs/sign-in.md).
 */
class PostgrestHttp(
    private val http: HttpClient,
    baseUrl: String,
    private val publishableKey: String,
    private val refreshSession: suspend () -> Boolean = { false },
    private val endSession: suspend () -> Unit = {},
    private val accessToken: suspend () -> String?,
) {
    private val restUrl = baseUrl.trimEnd('/') + "/rest/v1/"

    /** Sends a request to [path] (a table, or `rpc/<function>`) and returns the body of a success. */
    suspend fun send(method: HttpMethod, path: String, configure: HttpRequestBuilder.() -> Unit = {}): String {
        var (status, text) = call(method, path, configure)
        if (status == HttpStatusCode.Unauthorized && refreshSession()) {
            call(method, path, configure).let { (again, body) ->
                status = again
                text = body
            }
            if (status == HttpStatusCode.Unauthorized) {
                endSession()
                throw NotSignedInException("The server ended the session.")
            }
        }
        if (status.isSuccess()) return text

        // A 401 whose refresh did not come back: offline, or the refresh was refused and the
        // session already ended with it.
        val temporary = status == HttpStatusCode.Unauthorized || status == HttpStatusCode.RequestTimeout ||
            status == HttpStatusCode.TooManyRequests || status.value >= 500
        val message = "HTTP ${status.value}: ${text.take(300)}"
        throw if (temporary) RemoteUnavailableException(message) else RemoteRejectedException(message, sqlState(text))
    }

    private suspend fun call(method: HttpMethod, path: String, configure: HttpRequestBuilder.() -> Unit): Pair<HttpStatusCode, String> {
        val token = accessToken() ?: throw NotSignedInException("Not signed in.")
        try {
            val response = http.request(restUrl + path) {
                this.method = method
                header("apikey", publishableKey)
                bearerAuth(token)
                accept(ContentType.Application.Json)
                configure()
            }
            return response.status to response.bodyAsText()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: IOException) {
            // Includes Ktor's timeouts, which are IOExceptions too.
            throw RemoteUnavailableException("The server can't be reached.", error)
        }
    }

    // PostgREST reports a database error as {"code": "40001", "message": ...}.
    private fun sqlState(body: String): String? = runCatching {
        ((Json.parseToJsonElement(body) as? JsonObject)?.get("code") as? JsonPrimitive)?.content
    }.getOrNull()
}
