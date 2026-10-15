package com.goalmaker.app.data.auth

import com.goalmaker.app.application.auth.PcSignInApprovals
import com.goalmaker.app.application.auth.PcSignInRequest
import com.goalmaker.app.data.supabase.PostgrestHttp
import com.goalmaker.app.domain.sync.SyncRules
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** [PcSignInApprovals] over PostgREST: the two functions for the signed-in phone in 0028. */
class PostgrestPcSignInApprovals(private val postgrest: PostgrestHttp) : PcSignInApprovals {
    override suspend fun look(scanSecret: String): PcSignInRequest? {
        val text = postgrest.send(HttpMethod.Post, "rpc/pc_sign_in_look") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("scan_secret", scanSecret) }.toString())
        }
        val row = (Json.parseToJsonElement(text) as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
        return PcSignInRequest(
            device = row.text("device") ?: return null,
            createdAt = row.text("created_at")?.let(SyncRules::instantOf) ?: return null,
            expiresAt = row.text("expires_at")?.let(SyncRules::instantOf) ?: return null,
        )
    }

    override suspend fun answer(scanSecret: String, approve: Boolean): Boolean {
        val text = postgrest.send(HttpMethod.Post, "rpc/pc_sign_in_answer") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("scan_secret", scanSecret); put("approve", approve) }.toString())
        }
        // A function returning boolean answers with a bare JSON true or false.
        return (Json.parseToJsonElement(text) as? JsonPrimitive)?.booleanOrNull == true
    }

    private fun JsonObject.text(name: String): String? = (this[name] as? JsonPrimitive)?.takeUnless { it == JsonNull }?.content
}
