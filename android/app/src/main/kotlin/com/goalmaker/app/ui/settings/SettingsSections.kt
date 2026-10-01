package com.goalmaker.app.ui.settings

import com.goalmaker.app.application.update.UpdateRequest

/**
 * The jump list at the top of Settings: which sections there are, where a jump lands, and which
 * section the owner is reading as the page scrolls. Pure, so the rules have tests of their own.
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

    /** Where the update notification or the mark on the gear lands: the Updates section. */
    fun target(request: UpdateRequest?): SettingsSection? = request?.let { SettingsSection.UPDATES }

    /**
     * The section being read at [scroll]: the last one whose top has reached the top of the page
     * (within [slack]), or the last section once the page can't scroll any further, since a short
     * last section never reaches the top. A section the owner [jumped] to stays current at the bottom
     * while its top is still in view, so a jump to a short section near the end marks that one. The
     * first section before anything was measured.
     */
    fun current(
        sections: List<SettingsSection>,
        tops: Map<SettingsSection, Int>,
        scroll: Int,
        maxScroll: Int,
        slack: Int = 0,
        jumped: SettingsSection? = null,
    ): SettingsSection? {
        val measured = sections.filter { it in tops }
        if (measured.isEmpty()) return sections.firstOrNull()
        if (maxScroll > 0 && scroll >= maxScroll) {
            val top = jumped?.let(tops::get)
            return if (jumped in measured && top != null && top >= scroll - slack) jumped else measured.last()
        }
        return measured.lastOrNull { tops.getValue(it) <= scroll + slack } ?: measured.first()
    }
}
