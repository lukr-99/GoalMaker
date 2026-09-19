package com.goalmaker.app.ui.activity

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.goalmaker.app.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** One change in plain words: "Claude moved “Call the bank” to Mon 21 Sep" (docs/activity.md). */
@Composable
fun activitySentence(row: ActivityRow): String {
    val entry = row.entry
    val actor = stringResource(
        when (entry.actor) {
            "claude" -> R.string.activity_actor_claude
            "system" -> R.string.activity_actor_system
            else -> R.string.activity_actor_owner
        },
    )
    val subject = row.change.subject.orEmpty()
    val what = when (entry.entity) {
        "tasks" -> stringResource(R.string.activity_task, subject)
        "task_steps" -> stringResource(R.string.activity_step, subject)
        "areas" -> stringResource(R.string.activity_area, subject)
        "tags" -> stringResource(R.string.activity_tag, subject)
        "reminders" -> stringResource(R.string.activity_reminder)
        "task_tags" -> stringResource(R.string.activity_tag_link)
        "ritual_runs" -> stringResource(R.string.activity_ritual)
        "reviews" -> stringResource(R.string.activity_review)
        "goals" -> stringResource(R.string.activity_goal, subject)
        "goal_entries" -> stringResource(R.string.activity_goal_entry)
        else -> entry.entity
    }
    val days = DateTimeFormatter.ofPattern("EEE d MMM", LocalConfiguration.current.locales[0])
    return when (row.change.change) {
        "added" -> stringResource(R.string.activity_added, actor, what)
        "deleted" -> stringResource(R.string.activity_deleted, actor, what)
        "restored" -> stringResource(R.string.activity_restored, actor, what)
        "completed" -> stringResource(R.string.activity_completed, actor, what)
        "dropped" -> stringResource(R.string.activity_dropped, actor, what)
        "reopened" -> stringResource(R.string.activity_reopened, actor, what)
        "moved" -> row.change.day?.let { day ->
            stringResource(R.string.activity_moved, actor, what, LocalDate.parse(day).format(days))
        } ?: stringResource(R.string.activity_moved_off, actor, what)
        "renamed" -> stringResource(R.string.activity_renamed, actor, what)
        "archived" -> stringResource(R.string.activity_archived, actor, what)
        "unarchived" -> stringResource(R.string.activity_unarchived, actor, what)
        "checked" -> stringResource(R.string.activity_checked, actor, what)
        "unchecked" -> stringResource(R.string.activity_unchecked, actor, what)
        "handled" -> stringResource(R.string.activity_handled, actor, what)
        "snoozed" -> stringResource(R.string.activity_snoozed, actor, what)
        else -> stringResource(R.string.activity_edited, actor, what)
    }
}
