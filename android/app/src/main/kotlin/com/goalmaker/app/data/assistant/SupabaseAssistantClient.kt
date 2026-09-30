package com.goalmaker.app.data.assistant

import com.goalmaker.app.application.assistant.AssistantClient
import com.goalmaker.app.application.assistant.AssistantReply
import com.goalmaker.app.application.assistant.ChatMessage
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * [AssistantClient] over the `assistant` Edge Function (.scratch/m7-quick-chat/README.md, "The
 * call"): `POST /functions/v1/assistant` with the publishable key and the owner's access token, the
 * whole thread as `{"messages": [{"role", "text"}]}`. A `200 {"text"}` is the answer; a failure's
 * `{"error": code}` becomes its [AssistantReply].
 *
 * A token the server turns away (401) gets one [refreshSession] and one more try, like PostgREST
 * calls. Unlike them, a second 401 does not end the session: it is reported as signed out and the
 * sync, which meets the same session, decides.
 */
class SupabaseAssistantClient(
    private val http: HttpClient,
    baseUrl: String,
    private val publishableKey: String,
    private val refreshSession: suspend () -> Boolean = { false },
    private val accessToken: suspend () -> String?,
) : AssistantClient {
    private val url = baseUrl.trimEnd('/') + "/functions/v1/assistant"

    override suspend fun ask(thread: List<ChatMessage>): AssistantReply {
        val body = requestBody(thread)
        return try {
            var (status, text) = post(body) ?: return AssistantReply.SignedOut
            if (status == HttpStatusCode.Unauthorized) {
                // A refresh that could not happen is most likely no connection (docs/sign-in.md).
                if (!refreshSession()) return AssistantReply.Offline
                post(body)?.let { (again, answer) ->
                    status = again
                    text = answer
                } ?: return AssistantReply.SignedOut
                if (status == HttpStatusCode.Unauthorized) return AssistantReply.SignedOut
            }
            reply(status, text)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: HttpRequestTimeoutException) {
            // Reached, but the answer took too long: the server may still have made changes.
            AssistantReply.Failed
        } catch (_: IOException) {
            AssistantReply.Offline
        }
    }

    // Null when nobody is signed in, so the call never goes out without a session.
    private suspend fun post(body: String): Pair<HttpStatusCode, String>? {
        val token = accessToken() ?: return null
        val response = http.post(url) {
            header("apikey", publishableKey)
            bearerAuth(token)
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            setBody(body)
            // A request can run several tool rounds with the model, so it gets longer than a sync call.
            timeout { requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS }
        }
        return response.status to response.bodyAsText()
    }

    companion object {
        private const val REQUEST_TIMEOUT_MILLIS = 90_000L

        /** The request body: the thread in order, each line's role and text. */
        fun requestBody(thread: List<ChatMessage>): String = buildJsonObject {
            putJsonArray("messages") {
                thread.forEach { message ->
                    addJsonObject {
                        put("role", message.role.wire)
                        put("text", message.text)
                    }
                }
            }
        }.toString()

        /** What an answer means: the error code first, then the status when there is no code. */
        fun reply(status: HttpStatusCode, body: String): AssistantReply {
            val json = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull()
            if (status.isSuccess()) {
                val text = (json?.get("text") as? JsonPrimitive)?.takeIf { it.isString }?.content
                return text?.let(AssistantReply::Answer) ?: AssistantReply.Failed
            }
            return when ((json?.get("error") as? JsonPrimitive)?.content) {
                "unavailable" -> AssistantReply.Unavailable
                "rate_limited" -> AssistantReply.RateLimited
                "provider_limit" -> AssistantReply.ProviderLimit
                "bad_request" -> AssistantReply.BadRequest
                "failed" -> AssistantReply.Failed
                else -> when {
                    status == HttpStatusCode.Unauthorized -> AssistantReply.SignedOut
                    // No function by that name: this server has no chat.
                    status == HttpStatusCode.NotFound || status == HttpStatusCode.ServiceUnavailable -> AssistantReply.Unavailable
                    status == HttpStatusCode.TooManyRequests -> AssistantReply.RateLimited
                    status == HttpStatusCode.BadRequest -> AssistantReply.BadRequest
                    else -> AssistantReply.Failed
                }
            }
        }
    }
}
