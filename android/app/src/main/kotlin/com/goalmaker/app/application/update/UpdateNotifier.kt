package com.goalmaker.app.application.update

/** The quiet system notification that an update is ready, on its own low-importance channel. */
interface UpdateNotifier {
    /**
     * Shows "GoalMaker [version] is ready". False when it could not be shown, such as when the owner
     * has not allowed notifications.
     */
    fun show(version: String): Boolean

    /** Takes it down, if it is up. */
    fun cancel()
}
