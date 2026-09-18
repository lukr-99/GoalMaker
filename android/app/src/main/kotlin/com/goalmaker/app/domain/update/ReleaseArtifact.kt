package com.goalmaker.app.domain.update

/** One downloadable file of a release, located by its path inside the update channel bucket. */
data class ReleaseArtifact(
    val platform: ReleasePlatform,
    val path: String,
    val size: Long,
    val sha256: String,
)
