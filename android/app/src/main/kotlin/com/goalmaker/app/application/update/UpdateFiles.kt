package com.goalmaker.app.application.update

/** The update files kept in the app's private storage, named by their path in the release. */
interface UpdateFiles {
    /** The file kept for the release [path], with its size and SHA-256 measured now, or null when there is none. */
    fun measure(path: String): DownloadedArtifact?

    /** Deletes every kept file except the one for [keepPath], or all of them when it is null. */
    fun clean(keepPath: String? = null)

    /** Keeps nothing: every Install downloads first. */
    object None : UpdateFiles {
        override fun measure(path: String): DownloadedArtifact? = null

        override fun clean(keepPath: String?) = Unit
    }
}
