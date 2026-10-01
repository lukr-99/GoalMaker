package com.goalmaker.app.application.update

import com.goalmaker.app.domain.update.ManifestCheck
import com.goalmaker.app.domain.update.ReleasePlatform
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateServiceTest {
    private val hash = "a".repeat(64)
    private val manifestJson = """
        {"schema":1,"version":"0.3.0","androidVersionCode":3,"publishedAt":"2026-09-18T12:00:00Z",
         "artifacts":[{"platform":"android","path":"0.3.0/GoalMaker-0.3.0.apk","size":100,"sha256":"$hash"}]}
    """.trimIndent().encodeToByteArray()

    private val trusted = SignatureVerifier { _, signature -> signature == "good" }

    private class FakeChannel(
        var snapshot: ChannelSnapshot?,
        var downloaded: DownloadedArtifact = DownloadedArtifact("/cache/app.apk", 100, "a".repeat(64)),
    ) : ReleaseChannel {
        var downloads = 0

        override suspend fun fetchLatest(): ChannelSnapshot = snapshot ?: throw IOException("offline")

        override suspend fun download(path: String, onProgress: (bytesRead: Long) -> Unit): DownloadedArtifact {
            downloads++
            onProgress(downloaded.size)
            return downloaded
        }
    }

    private class FakeInstaller : UpdateInstaller {
        val launched = mutableListOf<String>()

        override fun launch(localPath: String) {
            launched += localPath
        }
    }

    private fun service(
        installed: String = "0.2.0",
        configured: Boolean = true,
        channel: ReleaseChannel = FakeChannel(ChannelSnapshot(manifestJson, "good")),
        installer: UpdateInstaller = FakeInstaller(),
    ) = UpdateService(installed, ReleasePlatform.ANDROID, configured, channel, ReleaseVerifier(trusted), installer)

    @Test
    fun `an unconfigured channel is reported, not contacted`() = runTest {
        val channel = FakeChannel(null)
        assertEquals(UpdateCheckResult.NotConfigured, service(configured = false, channel = channel).check())
    }

    @Test
    fun `development builds never update themselves`() = runTest {
        assertEquals(UpdateCheckResult.DevelopmentBuild, service(installed = "0.2.0-dev").check())
    }

    @Test
    fun `a newer signed release is offered`() = runTest {
        val result = service().check()
        assertTrue(result is UpdateCheckResult.Available)
        assertEquals("0.3.0", (result as UpdateCheckResult.Available).manifest.version.toString())
    }

    @Test
    fun `the same version is up to date`() = runTest {
        assertEquals(UpdateCheckResult.UpToDate("0.3.0"), service(installed = "0.3.0").check())
    }

    @Test
    fun `a badly signed manifest is untrusted`() = runTest {
        val channel = FakeChannel(ChannelSnapshot(manifestJson, "forged"))
        assertEquals(UpdateCheckResult.Untrusted, service(channel = channel).check())
    }

    @Test
    fun `a network failure is reported`() = runTest {
        val result = service(channel = FakeChannel(null)).check()
        assertEquals(UpdateCheckResult.Failed("offline"), result)
    }

    @Test
    fun `a verified download opens the installer`() = runTest {
        val installer = FakeInstaller()
        val subject = service(installer = installer)
        val available = subject.check() as UpdateCheckResult.Available
        assertEquals(InstallResult.InstallerOpened, subject.install(available))
        assertEquals(listOf("/cache/app.apk"), installer.launched)
    }

    @Test
    fun `a download with the wrong hash is never installed`() = runTest {
        val installer = FakeInstaller()
        val channel = FakeChannel(ChannelSnapshot(manifestJson, "good"), DownloadedArtifact("/cache/app.apk", 100, "b".repeat(64)))
        val subject = service(channel = channel, installer = installer)
        val available = subject.check() as UpdateCheckResult.Available
        assertEquals(InstallResult.DownloadCorrupted, subject.install(available))
        assertTrue(installer.launched.isEmpty())
    }

    @Test
    fun `a download with the wrong size is never installed`() = runTest {
        val installer = FakeInstaller()
        val channel = FakeChannel(ChannelSnapshot(manifestJson, "good"), DownloadedArtifact("/cache/app.apk", 99, "a".repeat(64)))
        val subject = service(channel = channel, installer = installer)
        val available = subject.check() as UpdateCheckResult.Available
        assertEquals(InstallResult.DownloadCorrupted, subject.install(available))
        assertTrue(installer.launched.isEmpty())
    }

    @Test
    fun `a found update waits until a check finds none`() = runTest {
        val channel = FakeChannel(ChannelSnapshot(manifestJson, "good"))
        val subject = service(channel = channel)
        assertNull(subject.waiting.value)

        val available = subject.check()
        assertSame(available, subject.waiting.value)

        channel.snapshot = null
        subject.check()
        assertNull(subject.waiting.value)
    }

    @Test
    fun `nothing waits when up to date, untrusted or a development build`() = runTest {
        val upToDate = service(installed = "0.3.0").also { it.check() }
        val forged = service(channel = FakeChannel(ChannelSnapshot(manifestJson, "forged"))).also { it.check() }
        val dev = service(installed = "0.2.0-dev").also { it.check() }
        assertNull(upToDate.waiting.value)
        assertNull(forged.waiting.value)
        assertNull(dev.waiting.value)
    }

    @Test
    fun `an install that fails leaves the update waiting`() = runTest {
        val channel = FakeChannel(ChannelSnapshot(manifestJson, "good"), DownloadedArtifact("/cache/app.apk", 100, "b".repeat(64)))
        val subject = service(channel = channel)
        val available = subject.check() as UpdateCheckResult.Available
        subject.install(available)
        assertSame(available, subject.waiting.value)
    }

    @Test
    fun `the verifier never parses unsigned bytes`() {
        val verifier = ReleaseVerifier { _, _ -> false }
        assertEquals(ManifestCheck.BadSignature, verifier.check(ChannelSnapshot("not json".encodeToByteArray(), "x")))
    }
}
