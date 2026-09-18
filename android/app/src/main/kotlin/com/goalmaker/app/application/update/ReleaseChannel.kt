package com.goalmaker.app.application.update

/** Reads the update channel (the private `releases` bucket). Throws on network or access errors. */
interface ReleaseChannel {
    /** The latest manifest's exact bytes and its detached base64 signature. */
    suspend fun fetchLatest(): ChannelSnapshot

    /** Downloads [path] from the channel to a private file and returns it. */
    suspend fun download(path: String, onProgress: (bytesRead: Long) -> Unit): DownloadedArtifact
}
