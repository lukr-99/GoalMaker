package com.goalmaker.app.domain.update

/** The outcome of checking a release manifest: signature first, then content. */
sealed interface ManifestCheck {
    data class Valid(val manifest: ReleaseManifest) : ManifestCheck

    data object BadSignature : ManifestCheck

    data class BadManifest(val reason: String) : ManifestCheck
}
