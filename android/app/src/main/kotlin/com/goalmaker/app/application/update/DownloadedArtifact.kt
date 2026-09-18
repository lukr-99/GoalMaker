package com.goalmaker.app.application.update

/** A file written to private storage, with the size and SHA-256 measured while writing it. */
data class DownloadedArtifact(
    val localPath: String,
    val size: Long,
    val sha256: String,
)
