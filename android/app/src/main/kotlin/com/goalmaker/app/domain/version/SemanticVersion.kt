package com.goalmaker.app.domain.version

/**
 * A Semantic Versioning 2.0.0 version. Build metadata is accepted and ignored for precedence.
 * Behavior is fixed by contracts/vectors/semantic-version.json, shared with the Windows app.
 */
data class SemanticVersion(
    val major: Long,
    val minor: Long,
    val patch: Long,
    val prerelease: List<String>,
) : Comparable<SemanticVersion> {

    val isPrerelease: Boolean get() = prerelease.isNotEmpty()

    /** True for local development builds, which carry the `dev` pre-release identifier. */
    val isDevelopmentBuild: Boolean get() = prerelease.firstOrNull() == DEVELOPMENT_IDENTIFIER

    override fun compareTo(other: SemanticVersion): Int {
        compareValues(major, other.major).takeIf { it != 0 }?.let { return it }
        compareValues(minor, other.minor).takeIf { it != 0 }?.let { return it }
        compareValues(patch, other.patch).takeIf { it != 0 }?.let { return it }
        if (prerelease.isEmpty() || other.prerelease.isEmpty()) {
            return when {
                prerelease.isEmpty() && other.prerelease.isEmpty() -> 0
                prerelease.isEmpty() -> 1
                else -> -1
            }
        }
        for (index in 0 until minOf(prerelease.size, other.prerelease.size)) {
            val result = compareIdentifiers(prerelease[index], other.prerelease[index])
            if (result != 0) return result
        }
        return compareValues(prerelease.size, other.prerelease.size)
    }

    override fun toString(): String =
        "$major.$minor.$patch" + if (prerelease.isEmpty()) "" else "-" + prerelease.joinToString(".")

    companion object {
        const val DEVELOPMENT_IDENTIFIER = "dev"

        private const val NUMERIC = "0|[1-9]\\d*"
        private const val IDENTIFIER = "(?:0|[1-9]\\d*|\\d*[A-Za-z-][0-9A-Za-z-]*)"
        private val pattern = Regex(
            "^($NUMERIC)\\.($NUMERIC)\\.($NUMERIC)" +
                "(?:-($IDENTIFIER(?:\\.$IDENTIFIER)*))?" +
                "(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$",
        )

        /** Parses [text] strictly (no leading `v`, no spaces); returns null when it isn't valid. */
        fun parse(text: String): SemanticVersion? {
            val match = pattern.matchEntire(text) ?: return null
            val (major, minor, patch, prerelease) = match.destructured
            return SemanticVersion(
                major = major.toLongOrNull() ?: return null,
                minor = minor.toLongOrNull() ?: return null,
                patch = patch.toLongOrNull() ?: return null,
                prerelease = if (prerelease.isEmpty()) emptyList() else prerelease.split('.'),
            )
        }

        private fun compareIdentifiers(left: String, right: String): Int {
            val leftNumber = left.takeIf { it.all(Char::isDigit) }?.toLongOrNull()
            val rightNumber = right.takeIf { it.all(Char::isDigit) }?.toLongOrNull()
            return when {
                leftNumber != null && rightNumber != null -> compareValues(leftNumber, rightNumber)
                leftNumber != null -> -1
                rightNumber != null -> 1
                else -> left.compareTo(right)
            }
        }
    }
}
