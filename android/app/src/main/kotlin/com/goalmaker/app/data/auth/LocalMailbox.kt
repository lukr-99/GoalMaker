package com.goalmaker.app.data.auth

import com.goalmaker.app.application.auth.DevMailbox
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads the sign-in code out of the mailbox the local Supabase stack catches its mail in
 * (docs/sign-in.md). Dev builds only, and only against that stack, which [DevMailbox.of] decides.
 * Nothing here ever reaches the cloud project, and a mailbox that is not there, is slow, or holds no
 * code is simply no code: the owner types it themselves.
 */
class LocalMailbox(private val http: HttpClient, private val mailbox: String) {
    private val json = Json { ignoreUnknownKeys = true }

    /** The code in the newest message to [email], or null. */
    suspend fun codeFor(email: String): String? = try {
        val inbox = json.parseToJsonElement(http.get("$mailbox/api/v1/messages?limit=20").bodyAsText()).jsonObject
        inbox["messages"]?.jsonArray.orEmpty()
            .map { it.jsonObject }
            .filter { isFor(it, email) }
            .firstNotNullOfOrNull { codeIn(it) }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    private fun isFor(message: JsonObject, email: String): Boolean =
        message["To"]?.jsonArray.orEmpty().any {
            it.jsonObject["Address"]?.jsonPrimitive?.content.equals(email, ignoreCase = true)
        }

    private suspend fun codeIn(message: JsonObject): String? {
        DevMailbox.codeIn(message["Snippet"]?.jsonPrimitive?.content)?.let { return it }
        val id = message["ID"]?.jsonPrimitive?.content ?: return null
        val body = json.parseToJsonElement(http.get("$mailbox/api/v1/message/$id").bodyAsText()).jsonObject
        return DevMailbox.codeIn(body["Text"]?.jsonPrimitive?.content)
            ?: DevMailbox.codeIn(body["HTML"]?.jsonPrimitive?.content)
    }
}
