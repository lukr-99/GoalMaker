package com.goalmaker.app.application.update

import com.goalmaker.app.domain.update.ManifestCheck
import com.goalmaker.app.domain.update.ReleasePlatform
import com.goalmaker.app.domain.update.UpdatePolicy
import com.goalmaker.app.domain.version.SemanticVersion
import kotlinx.coroutines.CancellationException

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
) {
    suspend fun check(): UpdateCheckResult {
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

    suspend fun install(update: UpdateCheckResult.Available, onProgress: (Float) -> Unit = {}): InstallResult {
        val expected = update.artifact
        val downloaded = try {
            channel.download(expected.path) { read -> onProgress((read.toFloat() / expected.size).coerceIn(0f, 1f)) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return InstallResult.Failed(error.message ?: error::class.simpleName.orEmpty())
        }
        if (downloaded.size != expected.size || !downloaded.sha256.equals(expected.sha256, ignoreCase = true)) {
            return InstallResult.DownloadCorrupted
        }
        installer.launch(downloaded.localPath)
        return InstallResult.InstallerOpened
    }
}
