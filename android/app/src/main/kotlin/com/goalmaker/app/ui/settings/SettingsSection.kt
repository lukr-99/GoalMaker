package com.goalmaker.app.ui.settings

import androidx.annotation.StringRes
import com.goalmaker.app.R

/**
 * One card on the Settings screen, in the order the screen shows them, with its id in
 * contracts/vectors/settings.json, its title and its one-line description.
 */
enum class SettingsSection(val id: String, @StringRes val title: Int, @StringRes val description: Int) {
    /** Only there while something went wrong (docs/problems.md). */
    PROBLEMS("problems", R.string.problems_title, R.string.settings_problems_description),
    ACCOUNT("account", R.string.settings_account, R.string.settings_account_description),
    APPEARANCE("appearance", R.string.settings_appearance, R.string.settings_appearance_description),
    PLANNING("planning", R.string.settings_planning, R.string.settings_planning_description),
    AREAS("areas", R.string.areas_title, R.string.settings_areas_description),
    CLAUDE("claude", R.string.settings_claude, R.string.settings_claude_description),
    DATA("data", R.string.settings_backup, R.string.settings_data_description),
    UPDATES("updates", R.string.settings_updates, R.string.settings_updates_description),
    ABOUT("about", R.string.settings_about, R.string.settings_about_description),

    /** Only in a dev build. */
    DEVELOPER("developer", R.string.settings_developer, R.string.settings_developer_description),
}
