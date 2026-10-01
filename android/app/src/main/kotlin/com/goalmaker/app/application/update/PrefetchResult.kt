package com.goalmaker.app.application.update

/** What fetching an update ahead of Install did. */
sealed interface PrefetchResult {
    /** The file is in private storage and matches the signed manifest's size and SHA-256. */
    data class Ready(val localPath: String) : PrefetchResult

    /** The file did not match the manifest and was deleted. */
    data object Corrupted : PrefetchResult

    /** The download did not finish (offline, GitHub down); try again later. */
    data class Failed(val detail: String) : PrefetchResult
}
