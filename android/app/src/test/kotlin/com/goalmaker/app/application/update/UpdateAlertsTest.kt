package com.goalmaker.app.application.update

import com.goalmaker.app.domain.update.ReleaseArtifact
import com.goalmaker.app.domain.update.ReleaseManifest
import com.goalmaker.app.domain.update.ReleasePlatform
import com.goalmaker.app.domain.update.UpdatePostponement
import com.goalmaker.app.domain.version.SemanticVersion
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The phone's side of a found update, without Android: one notification per version, cancelled
 * when none is found or the version is installed, Later for three days unless a newer version comes,
 * and the background download asked for with every find.
 */
class UpdateAlertsTest {
    private var now = Instant.parse("2026-10-01T09:00:00Z")
    private val waiting = MutableStateFlow<UpdateCheckResult.Available?>(null)
    private val memory = FakeMemory()
    private val notifier = FakeNotifier()
    private val downloads = FakeDownloads()
    private val files = FakeFiles()

    private class FakeMemory : UpdateMemory {
        var found: String? = null
        var notified: String? = null
        var postponed: UpdatePostponement? = null

        override fun updateFound() = found

        override fun updateNotified() = notified

        override fun setUpdateNotified(version: String?) {
            notified = version
        }

        override fun updatePostponed() = postponed

        override fun setUpdatePostponed(postponement: UpdatePostponement?) {
            postponed = postponement
        }
    }

    private class FakeNotifier : UpdateNotifier {
        var allowed = true
        val shown = mutableListOf<String>()
        var up: String? = null
        var cancels = 0

        override fun show(version: String): Boolean {
            if (!allowed) return false
            shown += version
            up = version
            return true
        }

        override fun cancel() {
            cancels++
            up = null
        }
    }

    private class FakeDownloads : UpdateDownloads {
        var scheduled = 0
        var cancelled = 0

        override fun schedule() {
            scheduled++
        }

        override fun cancel() {
            cancelled++
        }
    }

    private class FakeFiles : UpdateFiles {
        val cleaned = mutableListOf<String?>()

        override fun measure(path: String): DownloadedArtifact? = null

        override fun clean(keepPath: String?) {
            cleaned += keepPath
        }
    }

    private fun available(version: String): UpdateCheckResult.Available {
        val artifact = ReleaseArtifact(ReleasePlatform.ANDROID, "$version/GoalMaker-$version.apk", 100, "a".repeat(64))
        return UpdateCheckResult.Available(
            ReleaseManifest(SemanticVersion.parse(version)!!, 5, "2026-10-01T08:00:00Z", null, listOf(artifact)),
            artifact,
        )
    }

    private fun alerts(installed: String = "1.3.0", enabled: Boolean = true) =
        UpdateAlerts(installed, enabled, waiting, memory, notifier, downloads, files) { now }

    // What a check does: the service keeps what it found, then the alerts hear about it.
    private fun UpdateAlerts.checked(result: UpdateCheckResult) {
        if (result !is UpdateCheckResult.Failed) waiting.value = result as? UpdateCheckResult.Available
        afterCheck(result)
    }

    @Test
    fun `a found version is notified once, however often it is found again`() {
        val alerts = alerts()

        alerts.checked(available("1.4.0"))
        alerts.checked(available("1.4.0"))
        now = now.plus(Duration.ofDays(1))
        alerts.checked(available("1.4.0"))

        assertEquals(listOf("1.4.0"), notifier.shown)
        assertEquals("1.4.0", memory.notified)
        assertEquals("1.4.0", alerts.mark.value?.manifest?.version.toString())
    }

    @Test
    fun `a version notified before a restart is not notified again`() {
        memory.notified = "1.4.0"
        alerts().checked(available("1.4.0"))

        assertTrue(notifier.shown.isEmpty())
    }

    @Test
    fun `a newer version is notified too`() {
        val alerts = alerts()
        alerts.checked(available("1.4.0"))
        alerts.checked(available("1.5.0"))

        assertEquals(listOf("1.4.0", "1.5.0"), notifier.shown)
    }

    @Test
    fun `without permission nothing is shown, and it is shown once notifications are allowed`() {
        val alerts = alerts()
        notifier.allowed = false
        alerts.checked(available("1.4.0"))
        assertNull(memory.notified)
        assertEquals("1.4.0", alerts.mark.value?.manifest?.version.toString())

        notifier.allowed = true
        alerts.checked(available("1.4.0"))
        assertEquals(listOf("1.4.0"), notifier.shown)
    }

    @Test
    fun `every find asks for the background download`() {
        val alerts = alerts()
        alerts.checked(available("1.4.0"))
        alerts.checked(available("1.4.0"))

        assertEquals(2, downloads.scheduled)
    }

    @Test
    fun `a check that finds none takes the notification, the download and the files away`() {
        val alerts = alerts()
        alerts.checked(available("1.4.0"))

        alerts.checked(UpdateCheckResult.UpToDate("1.3.0"))

        assertNull(notifier.up)
        assertEquals(1, downloads.cancelled)
        assertEquals(listOf<String?>(null), files.cleaned)
        assertNull(alerts.mark.value)
    }

    @Test
    fun `an untrusted release counts as none`() {
        val alerts = alerts()
        alerts.checked(available("1.4.0"))

        alerts.checked(UpdateCheckResult.Untrusted)

        assertNull(notifier.up)
        assertNull(alerts.mark.value)
    }

    @Test
    fun `a failed check changes nothing`() {
        val alerts = alerts()
        alerts.checked(available("1.4.0"))
        val mark = alerts.mark.value

        alerts.checked(UpdateCheckResult.Failed("offline"))

        assertEquals("1.4.0", notifier.up)
        assertEquals(0, downloads.cancelled)
        assertTrue(files.cleaned.isEmpty())
        assertSame(mark, alerts.mark.value)
    }

    @Test
    fun `the start after the version is installed takes its notification and files away`() {
        memory.notified = "1.4.0"
        memory.found = "1.4.0"
        notifier.up = "1.4.0"

        alerts(installed = "1.4.0").settle()

        assertNull(notifier.up)
        assertNull(memory.notified)
        assertEquals(listOf<String?>(null), files.cleaned)
    }

    @Test
    fun `a start before installing keeps the notification and the fetched file`() {
        memory.notified = "1.4.0"
        memory.found = "1.4.0"
        notifier.up = "1.4.0"

        alerts(installed = "1.3.0").settle()

        assertEquals("1.4.0", notifier.up)
        assertEquals("1.4.0", memory.notified)
        assertTrue(files.cleaned.isEmpty())
    }

    @Test
    fun `Later hides the mark and the notification for three days`() {
        val alerts = alerts()
        alerts.checked(available("1.4.0"))

        alerts.later("1.4.0")

        assertNull(notifier.up)
        assertNull(alerts.mark.value)
        assertEquals(now.plus(Duration.ofDays(3)), alerts.postponedUntil.value)
        assertEquals(UpdatePostponement("1.4.0", now.plus(Duration.ofDays(3))), memory.postponed)

        now = now.plus(Duration.ofDays(2))
        alerts.checked(available("1.4.0"))
        assertEquals(listOf("1.4.0"), notifier.shown)
        assertNull(notifier.up)
        assertNull(alerts.mark.value)
    }

    @Test
    fun `when Later runs out the mark and the notification come back once`() {
        val alerts = alerts()
        alerts.checked(available("1.4.0"))
        alerts.later("1.4.0")

        now = now.plus(Duration.ofDays(3))
        alerts.checked(available("1.4.0"))
        alerts.checked(available("1.4.0"))

        assertEquals(listOf("1.4.0", "1.4.0"), notifier.shown)
        assertEquals("1.4.0", alerts.mark.value?.manifest?.version.toString())
        assertNull(alerts.postponedUntil.value)
    }

    @Test
    fun `a newer version is not held back by Later`() {
        val alerts = alerts()
        alerts.checked(available("1.4.0"))
        alerts.later("1.4.0")

        alerts.checked(available("1.5.0"))

        assertEquals(listOf("1.4.0", "1.5.0"), notifier.shown)
        assertEquals("1.5.0", alerts.mark.value?.manifest?.version.toString())
        assertNull(alerts.postponedUntil.value)
    }

    @Test
    fun `Later survives a restart`() {
        alerts().apply {
            checked(available("1.4.0"))
            later("1.4.0")
        }

        val again = alerts()
        again.checked(available("1.4.0"))

        assertNull(again.mark.value)
        assertEquals(listOf("1.4.0"), notifier.shown)
    }

    @Test
    fun `opening the installer takes the notification down`() {
        val alerts = alerts()
        alerts.checked(available("1.4.0"))

        alerts.installing()

        assertNull(notifier.up)
    }

    @Test
    fun `a dev build or one without a channel does nothing`() {
        val alerts = alerts(enabled = false)
        alerts.checked(available("1.4.0"))
        alerts.later("1.4.0")
        alerts.settle()

        assertTrue(notifier.shown.isEmpty())
        assertEquals(0, notifier.cancels)
        assertEquals(0, downloads.scheduled)
        assertNull(memory.postponed)
        assertTrue(files.cleaned.isEmpty())
    }
}
