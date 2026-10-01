package com.goalmaker.app.ui.settings

import androidx.annotation.StringRes
import com.goalmaker.app.R

/** One card on the Settings screen, in the order the screen shows them, and its title. */
enum class SettingsSection(@StringRes val title: Int) {
    /** Only there while something went wrong (docs/problems.md). */
    PROBLEMS(R.string.problems_title),
    ACCOUNT(R.string.settings_account),
    APPEARANCE(R.string.settings_appearance),
    PLANNING(R.string.settings_planning),
    AREAS(R.string.areas_title),
    CLAUDE(R.string.settings_claude),
    BACKUP(R.string.settings_backup),
    UPDATES(R.string.settings_updates),
    ABOUT(R.string.settings_about),

    /** Only in a dev build. */
    DEVELOPER(R.string.settings_developer),
}
