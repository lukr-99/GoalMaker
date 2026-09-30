package com.goalmaker.app.ui.chat

/** Whether chat can run right now, and if not, why; quick-add works in every case. */
enum class ChatAvailability {
    AVAILABLE,
    OFFLINE,
    SIGNED_OUT,

    /** The server answered that it has no model key (`unavailable`). */
    NOT_SET_UP,
}
