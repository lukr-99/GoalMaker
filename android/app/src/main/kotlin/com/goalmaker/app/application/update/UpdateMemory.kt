package com.goalmaker.app.application.update

import com.goalmaker.app.domain.update.UpdatePostponement

/**
 * What the phone remembers about a found update between starts: the version the last check found,
 * which version the notification told the owner about, and the version put off with Later.
 */
interface UpdateMemory {
    /** The version the last check that reached the channel found waiting, or null when it found none. */
    fun updateFound(): String?

    /** The version the notification last told the owner about, or null. */
    fun updateNotified(): String?

    fun setUpdateNotified(version: String?)

    /** The version the owner put off with Later, and until when; null when nothing is put off. */
    fun updatePostponed(): UpdatePostponement?

    fun setUpdatePostponed(postponement: UpdatePostponement?)
}
