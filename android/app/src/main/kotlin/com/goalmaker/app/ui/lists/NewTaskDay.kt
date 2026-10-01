package com.goalmaker.app.ui.lists

/** The day the new task form offers: Today, Tomorrow or none, so the task waits in the Inbox. */
enum class NewTaskDay {
    TODAY,
    TOMORROW,
    NO_DAY,
    ;

    companion object {
        /** The form starts on the day of the list it was opened from. */
        fun of(tab: ListTab): NewTaskDay = when (tab) {
            ListTab.TODAY -> TODAY
            ListTab.TOMORROW -> TOMORROW
            ListTab.INBOX -> NO_DAY
        }
    }
}
