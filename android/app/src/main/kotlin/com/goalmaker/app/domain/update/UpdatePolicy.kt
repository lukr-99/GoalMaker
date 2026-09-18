package com.goalmaker.app.domain.update

import com.goalmaker.app.domain.version.SemanticVersion

/**
 * Decides whether an available release should be offered to the installed app. A development build
 * never auto-updates (CodePrint delivery rule), and pre-releases are never offered.
 * Fixed by the offerUpdate cases in contracts/vectors/semantic-version.json.
 */
object UpdatePolicy {
    fun shouldOffer(installed: String, available: String): Boolean {
        val current = SemanticVersion.parse(installed) ?: return false
        val candidate = SemanticVersion.parse(available) ?: return false
        if (current.isDevelopmentBuild || candidate.isPrerelease) return false
        return candidate > current
    }
}
