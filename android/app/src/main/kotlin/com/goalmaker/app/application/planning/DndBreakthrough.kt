package com.goalmaker.app.application.planning

/**
 * Whether an important reminder rings through the phone's Do Not Disturb (docs/reminders.md, spec
 * story 57). Only the owner can allow it, on the system page of the important reminders' channel
 * ("Override Do Not Disturb"), so Settings shows where it stands and opens that page; while the app's
 * notifications are off altogether, it opens the app's notification page instead.
 */
enum class DndBreakthrough {
    /** The important reminders' channel overrides Do Not Disturb. */
    ALLOWED,

    /** The channel is on, but Do Not Disturb still silences it. */
    NOT_ALLOWED,

    /** The owner switched the important reminders' channel off. */
    CHANNEL_OFF,

    /** The owner switched the app's notifications off, or never allowed them. */
    NOTIFICATIONS_OFF,
    ;

    /** Whether the page to open is the app's notification page rather than the channel's. */
    val opensAppPage: Boolean get() = this == NOTIFICATIONS_OFF

    companion object {
        /**
         * Where it stands, from whether the app may notify at all ([notificationsOn]), whether the
         * channel is on ([channelOn]; one not made yet counts as on) and whether it bypasses DND.
         */
        fun of(notificationsOn: Boolean, channelOn: Boolean, bypassesDnd: Boolean): DndBreakthrough = when {
            !notificationsOn -> NOTIFICATIONS_OFF
            !channelOn -> CHANNEL_OFF
            bypassesDnd -> ALLOWED
            else -> NOT_ALLOWED
        }
    }
}
