package com.goalmaker.app.ui.nav

import com.goalmaker.app.ui.lists.ListTab

/**
 * The places the bottom bar switches between, all tabs of [MainScreen]. The three lists share one
 * screen; Projects and the Calendar each have their own (spec stories 43 to 50, docs/projects.md,
 * docs/calendar.md).
 */
enum class MainDestination {
    TODAY,
    TOMORROW,
    INBOX,
    PROJECTS,
    CALENDAR,
    ;

    /** The list this destination shows, or null when it is a screen of its own. */
    fun tab(): ListTab? = when (this) {
        TODAY -> ListTab.TODAY
        TOMORROW -> ListTab.TOMORROW
        INBOX -> ListTab.INBOX
        PROJECTS, CALENDAR -> null
    }
}
