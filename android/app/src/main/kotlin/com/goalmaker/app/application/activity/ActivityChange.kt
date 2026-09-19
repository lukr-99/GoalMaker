package com.goalmaker.app.application.activity

/**
 * What one activity entry did (docs/activity.md): [change] is added, deleted, restored, completed,
 * dropped, reopened, moved, renamed, archived, unarchived, checked, unchecked, handled, snoozed or
 * edited; [subject] is the row's title or name when it has one; [day] is where a moved task went.
 */
data class ActivityChange(
    val change: String,
    val subject: String?,
    val day: String? = null,
)
