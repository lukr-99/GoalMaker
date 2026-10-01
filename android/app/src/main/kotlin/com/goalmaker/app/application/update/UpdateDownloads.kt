package com.goalmaker.app.application.update

/**
 * Fetches the waiting update's file in the background, once the phone is on an unmetered network
 * and its battery is not low.
 */
interface UpdateDownloads {
    /** Asks for the download once; asking again while one waits or runs changes nothing. */
    fun schedule()

    /** Drops a download that has not run yet. */
    fun cancel()
}
