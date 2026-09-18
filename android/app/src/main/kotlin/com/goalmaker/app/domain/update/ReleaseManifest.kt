package com.goalmaker.app.domain.update

import com.goalmaker.app.domain.version.SemanticVersion

/** The latest release as described by a verified release manifest (contracts/schemas). */
data class ReleaseManifest(
    val version: SemanticVersion,
    val androidVersionCode: Int?,
    val publishedAt: String,
    val notes: String?,
    val artifacts: List<ReleaseArtifact>,
) {
    fun artifactFor(platform: ReleasePlatform): ReleaseArtifact? = artifacts.firstOrNull { it.platform == platform }
}
