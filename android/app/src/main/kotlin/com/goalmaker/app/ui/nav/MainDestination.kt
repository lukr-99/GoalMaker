package com.goalmaker.app.ui.nav

import com.goalmaker.app.ui.lists.ListTab

/**
 * The places the bottom bar switches between. The three lists are tabs of one screen; Projects and
 * the Calendar are screens of their own, which the bar reaches without going through the overflow
 * menu (spec stories 43 to 50, docs/projects.md, docs/calendar.md).
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

    companion object {
        /** The destination a list tab stands for. */
        fun of(tab: ListTab): MainDestination = when (tab) {
            ListTab.TODAY -> TODAY
            ListTab.TOMORROW -> TOMORROW
            ListTab.INBOX -> INBOX
        }
    }
}
