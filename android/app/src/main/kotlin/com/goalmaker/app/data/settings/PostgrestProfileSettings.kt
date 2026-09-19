package com.goalmaker.app.data.settings

import com.goalmaker.app.application.settings.ProfileSettings
import com.goalmaker.app.data.supabase.PostgrestHttp
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** [ProfileSettings] over PostgREST; row security limits the update to the owner's own profile. */
class PostgrestProfileSettings(private val postgrest: PostgrestHttp, private val userId: () -> String?) : ProfileSettings {
    override suspend fun update(timeZone: String, dayStartHour: Int) {
        val id = userId() ?: return
        postgrest.send(HttpMethod.Patch, "profiles") {
            parameter("id", "eq.$id")
            header("Prefer", "return=minimal")
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("time_zone", timeZone); put("day_rollover_hour", dayStartHour) }.toString())
        }
    }
}
