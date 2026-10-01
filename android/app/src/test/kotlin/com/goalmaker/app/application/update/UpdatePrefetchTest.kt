package com.goalmaker.app.application.update

import com.goalmaker.app.domain.update.ReleaseArtifact
import com.goalmaker.app.domain.update.ReleaseManifest
import com.goalmaker.app.domain.update.ReleasePlatform
import com.goalmaker.app.domain.version.SemanticVersion
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The APK fetched ahead of Install: kept only when it matches the signed manifest's size and SHA-256,
 * deleted when it doesn't, and opened at once by Install. Without one, Install still downloads first.
 */
class UpdatePrefetchTest {
    private val hash = "b".repeat(64)
    private val path = "1.4.0/GoalMaker-1.4.0.apk"
    private val artifact = ReleaseArtifact(ReleasePlatform.ANDROID, path, 100, hash)
    private val update = UpdateCheckResult.Available(
        ReleaseManifest(SemanticVersion.parse("1.4.0")!!, 5, "2026-10-01T08:00:00Z", null, listOf(artifact)),
        artifact,
    )

    /** A folder in memory: what the fake channel writes is what [measure] finds. */
    private class FakeFiles : UpdateFiles {
        val kept = mutableMapOf<String, DownloadedArtifact>()
        val cleaned = mutableListOf<String?>()

        override fun measure(path: String): DownloadedArtifact? = kept[path]

        override fun clean(keepPath: String?) {
            cleaned += keepPath
            kept.keys.retainAll { it == keepPath }
        }
    }

    private class FakeChannel(private val files: FakeFiles, var size: Long, var sha256: String) : ReleaseChannel {
        var reachable = true
        var downloads = 0

        override suspend fun fetchLatest(): ChannelSnapshot = throw IOException("not used")

        override suspend fun download(path: String, onProgress: (bytesRead: Long) -> Unit): DownloadedArtifact {
            downloads++
            if (!reachable) throw IOException("offline")
            onProgress(size)
            return DownloadedArtifact("/cache/updates/${path.substringAfterLast('/')}", size, sha256).also { files.kept[path] = it }
        }
    }

    private class FakeInstaller : UpdateInstaller {
        val launched = mutableListOf<String>()

        override fun launch(localPath: String) {
            launched += localPath
        }
    }

    private val files = FakeFiles()
    private val installer = FakeInstaller()

    private fun service(channel: ReleaseChannel) = UpdateService(
        installedVersion = "1.3.0",
        platform = ReleasePlatform.ANDROID,
        channelConfigured = true,
        channel = channel,
        verifier = ReleaseVerifier { _, _ -> true },
        installer = installer,
        files = files,
    )

    @Test
    fun `a download matching the manifest's size and hash is kept and offered`() = runTest {
        val channel = FakeChannel(files, 100, hash.uppercase())
        val updates = service(channel)

        val result = updates.prefetch(update)

        assertEquals(PrefetchResult.Ready("/cache/updates/GoalMaker-1.4.0.apk"), result)
        assertEquals("/cache/updates/GoalMaker-1.4.0.apk", updates.ready(update))
        assertEquals(listOf<String?>(path), files.cleaned)
        assertTrue(installer.launched.isEmpty())
    }

    @Test
    fun `a download with the wrong hash is deleted and never offered`() = runTest {
        val updates = service(FakeChannel(files, 100, "c".repeat(64)))

        assertEquals(PrefetchResult.Corrupted, updates.prefetch(update))
        assertNull(updates.ready(update))
        assertTrue(files.kept.isEmpty())
    }

    @Test
    fun `a download with the wrong size is deleted and never offered`() = runTest {
        val updates = service(FakeChannel(files, 99, hash))

        assertEquals(PrefetchResult.Corrupted, updates.prefetch(update))
        assertNull(updates.ready(update))
        assertTrue(files.kept.isEmpty())
    }

    @Test
    fun `a download cut short is a failure to try again`() = runTest {
        val channel = FakeChannel(files, 100, hash).apply { reachable = false }

        assertTrue(service(channel).prefetch(update) is PrefetchResult.Failed)
        assertTrue(files.kept.isEmpty())
    }

    @Test
    fun `a file already fetched is not fetched again`() = runTest {
        val channel = FakeChannel(files, 100, hash)
        val updates = service(channel)
        updates.prefetch(update)

        updates.prefetch(update)

        assertEquals(1, channel.downloads)
    }

    @Test
    fun `Install opens a file fetched ahead without downloading`() = runTest {
        val channel = FakeChannel(files, 100, hash)
        val updates = service(channel)
        updates.prefetch(update)
        var progress = 0f

        assertEquals(InstallResult.InstallerOpened, updates.install(update) { progress = it })

        assertEquals(1, channel.downloads)
        assertEquals(1f, progress)
        assertEquals(listOf("/cache/updates/GoalMaker-1.4.0.apk"), installer.launched)
    }

    @Test
    fun `a fetched file that changed since is downloaded again before Install`() = runTest {
        val channel = FakeChannel(files, 100, hash)
        val updates = service(channel)
        updates.prefetch(update)
        files.kept[path] = DownloadedArtifact("/cache/updates/GoalMaker-1.4.0.apk", 100, "d".repeat(64))

        assertEquals(InstallResult.InstallerOpened, updates.install(update))

        assertEquals(2, channel.downloads)
        assertEquals(1, installer.launched.size)
    }

    @Test
    fun `without a file fetched ahead Install still downloads, verifies and opens the installer`() = runTest {
        val channel = FakeChannel(files, 100, hash)

        assertEquals(InstallResult.InstallerOpened, service(channel).install(update))

        assertEquals(1, channel.downloads)
        assertEquals(listOf("/cache/updates/GoalMaker-1.4.0.apk"), installer.launched)
    }

    @Test
    fun `a corrupted download at Install is deleted and not opened`() = runTest {
        val channel = FakeChannel(files, 100, "e".repeat(64))

        assertEquals(InstallResult.DownloadCorrupted, service(channel).install(update))

        assertTrue(installer.launched.isEmpty())
        assertTrue(files.kept.isEmpty())
    }
}
