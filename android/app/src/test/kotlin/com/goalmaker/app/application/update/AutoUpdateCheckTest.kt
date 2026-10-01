package com.goalmaker.app.application.update

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import com.goalmaker.app.data.settings.SharedPreferencesSettingsStore
import com.goalmaker.app.domain.update.ReleasePlatform
import java.io.IOException
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The quiet check for updates: a little after the app comes to the front, at most once a day, never
 * in a dev build, never installing, and silent when it fails. The last check sits in the real
 * settings store.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AutoUpdateCheckTest {
    private val hash = "a".repeat(64)
    private val manifestJson = """
        {"schema":1,"version":"0.3.0","androidVersionCode":3,"publishedAt":"2026-09-18T12:00:00Z",
         "artifacts":[{"platform":"android","path":"0.3.0/GoalMaker-0.3.0.apk","size":100,"sha256":"$hash"}]}
    """.trimIndent().encodeToByteArray()

    private val trusted = SignatureVerifier { _, signature -> signature == "good" }
    private val channel = FakeChannel()
    private val installer = FakeInstaller()
    private var now = Instant.parse("2026-10-01T09:00:00Z")
    private lateinit var settings: SharedPreferencesSettingsStore

    private inner class FakeChannel : ReleaseChannel {
        var reachable = true
        var fetches = 0
        var downloads = 0

        override suspend fun fetchLatest(): ChannelSnapshot {
            fetches++
            return if (reachable) ChannelSnapshot(manifestJson, "good") else throw IOException("offline")
        }

        override suspend fun download(path: String, onProgress: (bytesRead: Long) -> Unit): DownloadedArtifact {
            downloads++
            return DownloadedArtifact("/cache/app.apk", 100, hash)
        }
    }

    private class FakeInstaller : UpdateInstaller {
        val launched = mutableListOf<String>()

        override fun launch(localPath: String) {
            launched += localPath
        }
    }

    @Before
    fun setUp() {
        val preferences = RuntimeEnvironment.getApplication().getSharedPreferences("update-check-test", Context.MODE_PRIVATE)
        preferences.edit(commit = true) { clear() }
        settings = SharedPreferencesSettingsStore(preferences)
    }

    private fun service(installed: String = "0.2.0", configured: Boolean = true) =
        UpdateService(installed, ReleasePlatform.ANDROID, configured, channel, ReleaseVerifier(trusted), installer)

    private fun check(updates: UpdateService) = AutoUpdateCheck(updates, settings, { now })

    @Test
    fun `checks a moment after the start when the last check is older than a day`() = runTest {
        settings.setUpdateCheck(now.minus(Duration.ofDays(2)), null)
        val updates = service()
        val auto = check(updates)

        launch { auto.afterStart() }
        advanceTimeBy(AutoUpdateCheck.START_DELAY.inWholeMilliseconds - 1)
        runCurrent()
        assertEquals(0, channel.fetches)

        advanceUntilIdle()
        assertEquals(1, channel.fetches)
        assertNotNull(updates.waiting.value)
        assertEquals(now, auto.lastChecked.value)
        assertEquals("0.3.0", settings.updateFound())
    }

    @Test
    fun `checks on the first start ever`() = runTest {
        assertTrue(check(service()).afterStart() is UpdateCheckResult.Available)
        assertEquals(1, channel.fetches)
    }

    @Test
    fun `skips when the last check is recent`() = runTest {
        settings.setUpdateCheck(now.minus(Duration.ofHours(2)), null)
        val updates = service()
        val auto = check(updates)

        assertNull(auto.afterStart())
        assertFalse(auto.isDue())
        assertEquals(0, channel.fetches)
        assertNull(updates.waiting.value)
    }

    @Test
    fun `checks again a day later and not before`() = runTest {
        val auto = check(service())
        auto.checkIfDue()
        assertEquals(1, channel.fetches)

        now = now.plus(Duration.ofHours(23))
        assertNull(auto.checkIfDue())
        assertEquals(1, channel.fetches)

        now = now.plus(Duration.ofHours(1))
        assertNotNull(auto.checkIfDue())
        assertEquals(2, channel.fetches)
    }

    @Test
    fun `never downloads or installs`() = runTest {
        val updates = service()
        val auto = check(updates)

        auto.afterStart()
        now = now.plus(Duration.ofDays(1))
        auto.afterStart()

        assertNotNull(updates.waiting.value)
        assertEquals(0, channel.downloads)
        assertTrue(installer.launched.isEmpty())
    }

    @Test
    fun `a failure is quiet and tried again at the next start`() = runTest {
        channel.reachable = false
        val updates = service()
        val auto = check(updates)

        assertTrue(auto.afterStart() is UpdateCheckResult.Failed)
        assertNull(auto.lastChecked.value)
        assertNull(updates.waiting.value)

        channel.reachable = true
        auto.afterStart()

        assertEquals(2, channel.fetches)
        assertNotNull(updates.waiting.value)
        assertEquals(now, auto.lastChecked.value)
    }

    @Test
    fun `a failure clears the mark like a manual check and the next check brings it back`() = runTest {
        val updates = service()
        val auto = check(updates)
        auto.checkIfDue()
        val found = auto.lastChecked.value
        assertNotNull(updates.waiting.value)

        channel.reachable = false
        now = now.plus(Duration.ofDays(1))
        auto.checkIfDue()

        // The same rule as Check for updates: a check that finds none clears the mark.
        assertNull(updates.waiting.value)
        assertEquals(found, auto.lastChecked.value)
        assertEquals("0.3.0", settings.updateFound())

        channel.reachable = true
        auto.checkIfDue()
        assertNotNull(updates.waiting.value)
    }

    @Test
    fun `an update found before a restart is checked again to show the mark`() = runTest {
        settings.setUpdateCheck(now.minus(Duration.ofHours(1)), "0.3.0")
        val updates = service()
        val auto = check(updates)
        assertTrue(auto.isDue())

        auto.checkIfDue()

        assertEquals(1, channel.fetches)
        assertNotNull(updates.waiting.value)
        assertFalse(auto.isDue())
    }

    @Test
    fun `an up to date check forgets the update it found before`() = runTest {
        settings.setUpdateCheck(now.minus(Duration.ofHours(1)), "0.3.0")
        val auto = check(service(installed = "0.3.0"))

        auto.checkIfDue()

        assertNull(settings.updateFound())
        assertFalse(auto.isDue())
    }

    @Test
    fun `dev builds and builds without a channel never check`() = runTest {
        for ((installed, configured) in listOf("0.2.0-dev" to true, "0.2.0" to false)) {
            val auto = check(service(installed, configured))
            assertFalse(auto.isDue())
            assertNull(auto.afterStart())
            assertNull(auto.checkIfDue())
        }
        assertEquals(0, channel.fetches)
        assertNull(settings.updatesCheckedAt.value)
    }

    @Test
    fun `a check the owner asks for is written down too`() = runTest {
        settings.setUpdateCheck(now.minus(Duration.ofHours(2)), null)
        val auto = check(service())

        assertTrue(auto.checkNow() is UpdateCheckResult.Available)
        assertEquals(now, settings.updatesCheckedAt.value)
    }
}
