package com.goalmaker.app.application.activity

import java.time.Instant
import kotlinx.serialization.json.JsonObject

/**
 * One row of the server's activity log (supabase/migrations/0004 and 0007): who changed which row,
 * how, the row before and after, and when it was undone, if it was.
 */
data class ActivityEntry(
    val id: Long,
    val entity: String,
    val entityId: String,
    val action: String,
    val actor: String,
    val before: JsonObject?,
    val after: JsonObject,
    val createdAt: Instant,
    val undoneAt: Instant?,
) {
    val change: ActivityChange get() = ActivityRules.change(entity, action, before, after)
}
