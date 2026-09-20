package com.goalmaker.app.ui.settings

/** What the Your data card is saying, once something has happened. */
enum class BackupOutcome {
    EXPORTED,
    RESTORED,

    /** The file was picked but could not be written: no room, or the folder is gone. */
    COULD_NOT_WRITE,

    /** The file was picked but could not be read at all, so nothing could be checked. */
    COULD_NOT_READ,
}
