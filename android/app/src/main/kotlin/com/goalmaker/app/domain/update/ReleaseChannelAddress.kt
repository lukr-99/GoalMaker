package com.goalmaker.app.domain.update

/**
 * Where the update channel's files live on GitHub Releases (ADR 0010), pinned by
 * contracts/vectors/release-channel.json. [updateUrl] is the repository's web address.
 */
class ReleaseChannelAddress(updateUrl: String) {
    private val base = updateUrl.trim().trimEnd('/')

    val manifest: String get() = "$base/releases/latest/download/manifest.json"
    val signature: String get() = "$base/releases/latest/download/manifest.sig"

    /** The page a person downloads a release from by hand (spec, story 94). */
    val releasesPage: String get() = "$base/releases/latest"

    /** The address of a manifest artifact `X.Y.Z/<file>`, or null for any other path. */
    fun artifact(path: String): String? {
        val version = path.substringBefore('/', missingDelimiterValue = "")
        val file = path.substringAfter('/', missingDelimiterValue = "")
        if (!VERSION.matches(version) || !FILE.matches(file) || file == "." || file == "..") return null
        return "$base/releases/download/v$version/$file"
    }

    private companion object {
        val VERSION = Regex("""(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)""")
        val FILE = Regex("[A-Za-z0-9._-]+")
    }
}
