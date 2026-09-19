package com.goalmaker.app.application.activity

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** What an activity log entry did, in the words the Activity screen uses (contracts/vectors/activity.json). */
object ActivityRules {
    fun change(entity: String, action: String, before: JsonObject?, after: JsonObject): ActivityChange {
        val subject = when (entity) {
            "tasks", "task_steps", "goals" -> after.text("title")
            "areas", "tags" -> after.text("name")
            else -> null
        }
        val change = when (action) {
            "create" -> "added"
            "delete" -> "deleted"
            "restore" -> "restored"
            else -> updated(entity, before, after)
        }
        return ActivityChange(change, subject, if (change == "moved") after.text("planned_date") else null)
    }

    private fun updated(entity: String, before: JsonObject?, after: JsonObject): String {
        fun changed(column: String) = before?.text(column) != after.text(column)
        return when (entity) {
            "tasks" -> when {
                changed("status") && after.text("status") == "done" -> "completed"
                changed("status") && after.text("status") == "dropped" -> "dropped"
                changed("status") && after.text("status") == "open" -> "reopened"
                changed("planned_date") -> "moved"
                changed("title") -> "renamed"
                else -> "edited"
            }
            "goals" -> when {
                changed("status") && after.text("status") == "done" -> "completed"
                changed("status") && after.text("status") == "dropped" -> "dropped"
                changed("status") && after.text("status") == "open" -> "reopened"
                changed("title") -> "renamed"
                else -> "edited"
            }
            "areas" -> when {
                changed("archived_at") -> if (after.text("archived_at") != null) "archived" else "unarchived"
                changed("name") -> "renamed"
                else -> "edited"
            }
            "tags" -> if (changed("name")) "renamed" else "edited"
            "task_steps" -> when {
                changed("done") -> if (after.text("done") == "true") "checked" else "unchecked"
                changed("title") -> "renamed"
                else -> "edited"
            }
            "reminders" -> when {
                changed("state") && after.text("state") in setOf("done", "dismissed") -> "handled"
                changed("state") && after.text("state") == "snoozed" -> "snoozed"
                else -> "edited"
            }
            else -> "edited"
        }
    }

    // A column's value as text; missing and JSON null are both null.
    private fun JsonObject.text(column: String): String? =
        (this[column] as? JsonPrimitive)?.takeUnless { it == JsonNull }?.content
}
