package com.goalmaker.app.application.update

import com.goalmaker.app.domain.update.ReleaseArtifact
import com.goalmaker.app.domain.update.ReleaseManifest

/** What "Check for updates" found. */
sealed interface UpdateCheckResult {
    /** No trusted key is built in, so the channel can't be verified (docs/setup). */
    data object NotConfigured : UpdateCheckResult

    /** Development builds never update themselves; install a release build instead. */
    data object DevelopmentBuild : UpdateCheckResult

    data class UpToDate(val latest: String) : UpdateCheckResult

    data class Available(val manifest: ReleaseManifest, val artifact: ReleaseArtifact) : UpdateCheckResult

    data object Untrusted : UpdateCheckResult

    data class Failed(val detail: String) : UpdateCheckResult
}
