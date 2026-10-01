package com.goalmaker.app.application.update

import com.goalmaker.app.domain.update.ManifestCheck
import com.goalmaker.app.domain.update.ReleaseArtifact
import com.goalmaker.app.domain.update.ReleasePlatform
import com.goalmaker.app.domain.update.UpdatePolicy
import com.goalmaker.app.domain.version.SemanticVersion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Release discovery → signature and content check → version policy → verified download → installer
 * launch, each behind its own seam (CodePrint updater rule). Never touches user data.
 */
class UpdateService(
    private val installedVersion: String,
    private val platform: ReleasePlatform,
    private val channelConfigured: Boolean,
    private val channel: ReleaseChannel,
    private val verifier: ReleaseVerifier,
    private val installer: UpdateInstaller,
    /** Where a file fetched ahead of Install waits; none keeps every Install a download first. */
    private val files: UpdateFiles = UpdateFiles.None,
) {
    private val found = MutableStateFlow<UpdateCheckResult.Available?>(null)

    /**
     * The update the last check found, or null: the mark on the way to Settings. Every check that
     * gets an answer replaces it, so it goes when one finds none; a check that fails leaves it, and a
     * new version starts without it.
     */
    val waiting: StateFlow<UpdateCheckResult.Available?> = found.asStateFlow()

    /**
     * Whether a check can reach anything at all: the build has a channel and is a release. A dev
     * build or one without the key answers every check without the network.
     */
    val canCheck: Boolean
        get() = channelConfigured && SemanticVersion.parse(installedVersion)?.isDevelopmentBuild == false

    /** A check that could not reach the channel (offline, GitHub down) leaves [waiting] as it was. */
    suspend fun check(): UpdateCheckResult = find().also { result ->
        if (result !is UpdateCheckResult.Failed) found.value = result as? UpdateCheckResult.Available
    }

    private suspend fun find(): UpdateCheckResult {
        if (!channelConfigured) return UpdateCheckResult.NotConfigured
        if (SemanticVersion.parse(installedVersion)?.isDevelopmentBuild != false) {
            return UpdateCheckResult.DevelopmentBuild
        }
        val snapshot = try {
            channel.fetchLatest()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return UpdateCheckResult.Failed(error.message ?: error::class.simpleName.orEmpty())
        }
        val manifest = when (val check = verifier.check(snapshot)) {
            is ManifestCheck.Valid -> check.manifest
            ManifestCheck.BadSignature, is ManifestCheck.BadManifest -> return UpdateCheckResult.Untrusted
        }
        val artifact = manifest.artifactFor(platform)
        return if (artifact != null && UpdatePolicy.shouldOffer(installedVersion, manifest.version.toString())) {
            UpdateCheckResult.Available(manifest, artifact)
        } else {
            UpdateCheckResult.UpToDate(manifest.version.toString())
        }
    }

    /**
     * The file kept for [update] when it still matches the signed manifest's size and SHA-256, so
     * Install can open it at once; null when there is none or it no longer matches.
     */
    fun ready(update: UpdateCheckResult.Available): String? =
        files.measure(update.artifact.path)?.takeIf { it.matches(update.artifact) }?.localPath

    /**
     * Fetches [update]'s file into private storage ahead of Install and checks it against the signed
     * manifest. A file that does not match is deleted, so it is never offered. Never installs.
     */
    suspend fun prefetch(update: UpdateCheckResult.Available): PrefetchResult {
        ready(update)?.let { return PrefetchResult.Ready(it) }
        val downloaded = try {
            channel.download(update.artifact.path) { }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            files.clean()
            return PrefetchResult.Failed(error.message ?: error::class.simpleName.orEmpty())
        }
        if (!downloaded.matches(update.artifact)) {
            files.clean()
            return PrefetchResult.Corrupted
        }
        files.clean(keepPath = update.artifact.path)
        return PrefetchResult.Ready(downloaded.localPath)
    }

    /**
     * Opens the platform installer for [update]: at once with a file fetched ahead and still
     * matching, or after a verified download. The installer asks the owner to confirm.
     */
    suspend fun install(update: UpdateCheckResult.Available, onProgress: (Float) -> Unit = {}): InstallResult {
        val expected = update.artifact
        ready(update)?.let { path ->
            onProgress(1f)
            installer.launch(path)
            return InstallResult.InstallerOpened
        }
        val downloaded = try {
            channel.download(expected.path) { read -> onProgress((read.toFloat() / expected.size).coerceIn(0f, 1f)) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return InstallResult.Failed(error.message ?: error::class.simpleName.orEmpty())
        }
        if (!downloaded.matches(expected)) {
            files.clean()
            return InstallResult.DownloadCorrupted
        }
        installer.launch(downloaded.localPath)
        return InstallResult.InstallerOpened
    }

    private fun DownloadedArtifact.matches(expected: ReleaseArtifact) =
        size == expected.size && sha256.equals(expected.sha256, ignoreCase = true)
}
