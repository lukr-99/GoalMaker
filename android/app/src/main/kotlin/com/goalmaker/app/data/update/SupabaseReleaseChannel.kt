package com.goalmaker.app.data.update

import com.goalmaker.app.application.update.ChannelSnapshot
import com.goalmaker.app.application.update.DownloadedArtifact
import com.goalmaker.app.application.update.ReleaseChannel
import com.goalmaker.app.data.supabase.SupabaseClientFactory
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the private `releases` bucket with the signed-in user's session (ADR 0004). Artifacts are
 * written to the cache folder that the FileProvider exposes to the package installer.
 */
class SupabaseReleaseChannel(
    private val client: SupabaseClient,
    private val updatesDirectory: File,
) : ReleaseChannel {

    override suspend fun fetchLatest(): ChannelSnapshot {
        val bucket = client.storage.from(SupabaseClientFactory.RELEASES_BUCKET)
        val manifest = bucket.downloadAuthenticated(MANIFEST_PATH)
        val signature = bucket.downloadAuthenticated(SIGNATURE_PATH).decodeToString().trim()
        return ChannelSnapshot(manifest, signature)
    }

    override suspend fun download(path: String, onProgress: (bytesRead: Long) -> Unit): DownloadedArtifact {
        val bytes = client.storage.from(SupabaseClientFactory.RELEASES_BUCKET).downloadAuthenticated(path)
        onProgress(bytes.size.toLong())
        return withContext(Dispatchers.IO) {
            updatesDirectory.mkdirs()
            updatesDirectory.listFiles()?.forEach(File::delete)
            val target = File(updatesDirectory, path.substringAfterLast('/'))
            target.writeBytes(bytes)
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            DownloadedArtifact(
                localPath = target.absolutePath,
                size = bytes.size.toLong(),
                sha256 = digest.joinToString("") { "%02x".format(it) },
            )
        }
    }

    private companion object {
        const val MANIFEST_PATH = "latest/manifest.json"
        const val SIGNATURE_PATH = "latest/manifest.sig"
    }
}
