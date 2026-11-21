package com.goalmaker.app.ui.settings

import androidx.annotation.StringRes
import com.goalmaker.app.R

/**
 * The pages Settings is split into (docs/design/spec.md, Settings), the way Tarot2Go splits off its
 * Appearance: a crowded group is one link card on the main page and a page of its own. Each page
 * keeps the section kit, so its cards, chips and hints work the same.
 */
enum class SettingsPage(val id: String, @StringRes val title: Int, val sections: List<SettingsSection>) {
    /** Everything else, and a link card for each page below. */
    MAIN("main", R.string.settings_title, SettingsSection.entries.filterNot { it == SettingsSection.UPDATES }),
    APPEARANCE("appearance", R.string.settings_appearance, listOf(SettingsSection.APPEARANCE)),
    PLANNING("planning", R.string.settings_planning, listOf(SettingsSection.PLANNING)),
    DATA("data", R.string.settings_data_page, listOf(SettingsSection.DATA, SettingsSection.UPDATES)),
    ;

    companion object {
        /** The page a section's card on the main page opens, or null when the card holds its rows itself. */
        fun opens(section: SettingsSection): SettingsPage? = when (section) {
            SettingsSection.APPEARANCE -> APPEARANCE
            SettingsSection.PLANNING -> PLANNING
            SettingsSection.DATA -> DATA
            else -> null
        }

        /** The page with [id], or the main page for an id it doesn't know. */
        fun of(id: String): SettingsPage = entries.firstOrNull { it.id == id } ?: MAIN
    }
}
