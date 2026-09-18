package com.goalmaker.app.domain.update

import com.goalmaker.app.domain.version.SemanticVersion
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Parses and validates release manifest bytes that already passed the signature check. Unknown
 * properties are ignored; every rule here is covered by contracts/vectors/release-manifest.json.
 */
object ReleaseManifestParser {
    private const val SUPPORTED_SCHEMA = 1
    private const val MAX_ARTIFACT_SIZE = 524_288_000L
    private val pathPattern = Regex("^[A-Za-z0-9._-]+(/[A-Za-z0-9._-]+)*$")
    private val sha256Pattern = Regex("^[0-9a-f]{64}$")

    fun parse(bytes: ByteArray): ManifestCheck {
        val root = try {
            Json.parseToJsonElement(bytes.decodeToString()) as? JsonObject
        } catch (_: SerializationException) {
            null
        } ?: return ManifestCheck.BadManifest("not a JSON object")

        val schema = root.primitive("schema")?.intOrNull
        if (schema != SUPPORTED_SCHEMA) return ManifestCheck.BadManifest("unsupported schema $schema")

        val versionText = root.text("version") ?: return ManifestCheck.BadManifest("version missing")
        val version = SemanticVersion.parse(versionText)
            ?: return ManifestCheck.BadManifest("version is not semantic: $versionText")

        val publishedAt = root.text("publishedAt") ?: return ManifestCheck.BadManifest("publishedAt missing")

        val artifactsJson = root["artifacts"] as? JsonArray
        if (artifactsJson.isNullOrEmpty()) return ManifestCheck.BadManifest("no artifacts")
        val artifacts = artifactsJson.map { element ->
            parseArtifact(element) ?: return ManifestCheck.BadManifest("invalid artifact $element")
        }

        val androidVersionCode = root.primitive("androidVersionCode")?.intOrNull?.takeIf { it >= 1 }
        if (artifacts.any { it.platform == ReleasePlatform.ANDROID } && androidVersionCode == null) {
            return ManifestCheck.BadManifest("android artifact without androidVersionCode")
        }

        return ManifestCheck.Valid(
            ReleaseManifest(
                version = version,
                androidVersionCode = androidVersionCode,
                publishedAt = publishedAt,
                notes = root.text("notes"),
                artifacts = artifacts,
            ),
        )
    }

    private fun parseArtifact(element: JsonElement): ReleaseArtifact? {
        val item = element as? JsonObject ?: return null
        val platform = item.text("platform")?.let(ReleasePlatform::fromWireName) ?: return null
        val path = item.text("path")?.takeIf { value ->
            pathPattern.matches(value) && value.split('/').none { it == ".." || it == "." }
        } ?: return null
        val size = item.primitive("size")?.takeUnless { it.isString }?.longOrNull
            ?.takeIf { it in 1..MAX_ARTIFACT_SIZE } ?: return null
        val sha256 = item.text("sha256")?.takeIf(sha256Pattern::matches) ?: return null
        return ReleaseArtifact(platform, path, size, sha256)
    }

    private fun JsonObject.primitive(name: String): JsonPrimitive? = this[name] as? JsonPrimitive

    private fun JsonObject.text(name: String): String? = primitive(name)?.takeIf { it.isString }?.contentOrNull
}
