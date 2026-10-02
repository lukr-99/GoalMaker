package com.goalmaker.app.ui.settings

import com.goalmaker.app.application.update.UpdateRequest
import com.goalmaker.app.domain.settings.SettingsPageRules

/**
 * The jump list at the top of Settings: which sections there are, whether the chip row shows,
 * where a deep link lands, and which section the owner is reading as the page scrolls. The rules
 * themselves are [SettingsPageRules], shared with Windows through contracts/vectors/settings.json.
 */
object SettingsSections {
    /** The sections in screen order: Problems only while there are some, Developer only in a dev build. */
    fun visible(hasProblems: Boolean, devBuild: Boolean): List<SettingsSection> =
        SettingsSection.entries.filter { section ->
            when (section) {
                SettingsSection.PROBLEMS -> hasProblems
                SettingsSection.DEVELOPER -> devBuild
                else -> true
            }
        }

    /** Whether the chip row shows: only with four sections or more. */
    fun showChips(sections: List<SettingsSection>): Boolean = SettingsPageRules.showNavigation(sections.size)

    /** Where the update notification or the mark on the gear lands: the Updates section, with the jump hint. */
    fun target(request: UpdateRequest?): SettingsSection? = request?.let { SettingsSection.UPDATES }

    /**
     * The section being read at [scroll] (px), with [line] the 80 dp line in px: see
     * [SettingsPageRules.current]. [pinned] is the section a jump went to.
     */
    fun current(
        sections: List<SettingsSection>,
        tops: Map<SettingsSection, Int>,
        scroll: Int,
        maxScroll: Int,
        line: Int,
        pinned: SettingsSection? = null,
    ): SettingsSection? = SettingsPageRules.current(sections, tops, scroll, maxScroll, pinned, line)
}
