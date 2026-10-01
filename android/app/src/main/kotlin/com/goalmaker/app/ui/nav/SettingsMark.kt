package com.goalmaker.app.ui.nav

import com.goalmaker.app.application.update.UpdateCheckResult
import com.goalmaker.app.domain.problems.Problem

/**
 * What the gear in the top bar wears: an accent badge with a download arrow while an update the
 * last check found waits, and the quiet dot for a problem nobody has read (docs/problems.md).
 */
data class SettingsMark(val update: Boolean, val problems: Boolean) {
    val shows: Boolean get() = update || problems

    companion object {
        fun of(waiting: UpdateCheckResult.Available?, problems: List<Problem>) =
            SettingsMark(update = waiting != null, problems = problems.any { it.unread })
    }
}
