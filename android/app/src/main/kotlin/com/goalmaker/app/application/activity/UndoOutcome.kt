package com.goalmaker.app.application.activity

/** What asking the server to undo a change came to (supabase/migrations/0007, undo_activity). */
enum class UndoOutcome {
    /** The row is back the way the entry found it. */
    UNDONE,

    /** The row changed after this entry, so undoing it would throw later work away. */
    CHANGED_SINCE,

    /** Someone undid it already, perhaps on the other device. */
    ALREADY_UNDONE,

    /** The entry is gone, or the change can't be undone. */
    NOT_POSSIBLE,
}
