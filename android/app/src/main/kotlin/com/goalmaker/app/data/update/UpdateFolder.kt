package com.goalmaker.app.data.update

import com.goalmaker.app.application.update.DownloadedArtifact
import com.goalmaker.app.application.update.UpdateFiles
import java.io.File
import java.security.MessageDigest

/**
 * The app-private folder update files are kept in (the cache folder the `updates` FileProvider
 * shares with the package installer). A file is named after the last part of its release path, and
 * is only given that name once it is complete, so a download cut short never looks like a whole one.
 */
class UpdateFolder(private val directory: File) : UpdateFiles {

    override fun measure(path: String): DownloadedArtifact? {
        val file = fileFor(directory, path)
        if (!file.isFile) return null
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return DownloadedArtifact(file.absolutePath, file.length(), digest.digest().toHex())
    }

    override fun clean(keepPath: String?) {
        val keep = keepPath?.let { fileFor(directory, it).name }
        directory.listFiles()?.filter { it.name != keep }?.forEach(File::delete)
    }

    companion object {
        private const val BUFFER_BYTES = 64 * 1024

        /** Where the release file at [path] is kept inside [directory]. */
        fun fileFor(directory: File, path: String) = File(directory, path.substringAfterLast('/'))

        fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    }
}
