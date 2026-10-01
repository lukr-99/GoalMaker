package com.goalmaker.app.data.update

/** The names the update notification's taps and buttons carry. */
object UpdateIntents {
    /** Open Settings at the update. */
    const val EXTRA_OPEN_UPDATE = "com.goalmaker.app.OPEN_UPDATE"

    /** Open Settings at the update and start Install there. */
    const val EXTRA_INSTALL_UPDATE = "com.goalmaker.app.INSTALL_UPDATE"

    /** Later, from the notification. */
    const val ACTION_LATER = "com.goalmaker.app.UPDATE_LATER"

    /** The version Later puts off. */
    const val EXTRA_VERSION = "com.goalmaker.app.UPDATE_VERSION"
}
