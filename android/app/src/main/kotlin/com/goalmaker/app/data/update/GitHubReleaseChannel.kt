package com.goalmaker.app.data.update

import com.goalmaker.app.application.update.ChannelSnapshot
import com.goalmaker.app.application.update.DownloadedArtifact
import com.goalmaker.app.application.update.ReleaseChannel
import com.goalmaker.app.domain.update.ReleaseChannelAddress
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the latest published GitHub release (ADR 0010). Nobody needs to be signed in: the manifest
 * signature and each artifact's SHA-256 are what make a download trusted. Artifacts are written to
 * the cache folder that the FileProvider exposes to the package installer.
 */
class GitHubReleaseChannel(
    private val http: HttpClient,
    private val address: ReleaseChannelAddress,
    private val updatesDirectory: File,
) : ReleaseChannel {

    override suspend fun fetchLatest(): ChannelSnapshot {
        val manifest = successful(http.get(address.manifest)).bodyAsBytes()
        val signature = successful(http.get(address.signature)).bodyAsBytes().decodeToString().trim()
        return ChannelSnapshot(manifest, signature)
    }

    override suspend fun download(path: String, onProgress: (bytesRead: Long) -> Unit): DownloadedArtifact {
        val url = address.artifact(path) ?: throw IOException("the release names a file it cannot hold: $path")
        return withContext(Dispatchers.IO) {
            updatesDirectory.mkdirs()
            updatesDirectory.listFiles()?.forEach(File::delete)
            val target = File(updatesDirectory, path.substringAfterLast('/'))
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            http.prepareGet(url).execute { response ->
                val body = successful(response).bodyAsChannel()
                val buffer = ByteArray(BUFFER_BYTES)
                target.outputStream().use { output ->
                    while (true) {
                        val read = body.readAvailable(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        size += read
                        onProgress(size)
                    }
                }
            }
            DownloadedArtifact(
                localPath = target.absolutePath,
                size = size,
                sha256 = digest.digest().joinToString("") { "%02x".format(it) },
            )
        }
    }

    private fun successful(response: HttpResponse): HttpResponse {
        if (!response.status.isSuccess()) throw IOException("the release server answered ${response.status.value}")
        return response
    }

    private companion object {
        const val BUFFER_BYTES = 64 * 1024
    }
}
