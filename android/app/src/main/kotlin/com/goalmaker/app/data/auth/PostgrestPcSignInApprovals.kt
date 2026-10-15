package com.goalmaker.app.data.auth

import com.goalmaker.app.application.auth.PcSignInAnswer
import com.goalmaker.app.application.auth.PcSignInApprovals
import com.goalmaker.app.application.auth.PcSignInRequest
import com.goalmaker.app.application.sync.RemoteRejectedException
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
import kotlinx.serialization.json.intOrNull
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
            choices = (row["choices"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.intOrNull },
        )
    }

    override suspend fun answer(scanSecret: String, approve: Boolean, picked: Int?): PcSignInAnswer {
        val text = postgrest.send(HttpMethod.Post, "rpc/pc_sign_in_answer") {
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("scan_secret", scanSecret)
                    put("approve", approve)
                    put("picked", picked)
                }.toString(),
            )
        }
        // A function returning text answers with a JSON string.
        return when ((Json.parseToJsonElement(text) as? JsonPrimitive)?.takeIf { it.isString }?.content) {
            "approved" -> PcSignInAnswer.APPROVED
            "declined" -> PcSignInAnswer.DECLINED
            "mismatch" -> PcSignInAnswer.MISMATCH
            "gone" -> PcSignInAnswer.GONE
            "no_email" -> PcSignInAnswer.NO_EMAIL
            else -> throw RemoteRejectedException("The server gave no answer the app knows: ${text.take(100)}")
        }
    }

    private fun JsonObject.text(name: String): String? = (this[name] as? JsonPrimitive)?.takeUnless { it == JsonNull }?.content
}
