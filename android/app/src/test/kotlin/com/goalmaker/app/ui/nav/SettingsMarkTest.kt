package com.goalmaker.app.ui.nav

import com.goalmaker.app.application.update.UpdateCheckResult
import com.goalmaker.app.domain.problems.Problem
import com.goalmaker.app.domain.update.ReleaseArtifact
import com.goalmaker.app.domain.update.ReleaseManifest
import com.goalmaker.app.domain.update.ReleasePlatform
import com.goalmaker.app.domain.version.SemanticVersion
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The mark on the gear: an update waiting, a problem nobody has read, both, or nothing. */
class SettingsMarkTest {
    private val artifact = ReleaseArtifact(ReleasePlatform.ANDROID, "1.1.0/GoalMaker-1.1.0.apk", 100, "a".repeat(64))
    private val update = UpdateCheckResult.Available(
        ReleaseManifest(SemanticVersion.parse("1.1.0")!!, 11, "2026-10-01T12:00:00Z", null, listOf(artifact)),
        artifact,
    )
    private val unread = Problem("sync", Instant.parse("2026-10-01T09:00:00Z"))

    @Test
    fun `an update waiting marks the gear`() {
        val mark = SettingsMark.of(update, emptyList())
        assertEquals(SettingsMark(update = true, problems = false), mark)
        assertTrue(mark.shows)
    }

    @Test
    fun `no update and nothing unread leaves the gear plain`() {
        val mark = SettingsMark.of(null, listOf(unread.copy(unread = false)))
        assertFalse(mark.shows)
    }

    @Test
    fun `an update and a problem both show`() {
        assertEquals(SettingsMark(update = true, problems = true), SettingsMark.of(update, listOf(unread)))
    }

    @Test
    fun `a problem alone is the dot without the update badge`() {
        val mark = SettingsMark.of(null, listOf(unread))
        assertTrue(mark.shows)
        assertFalse(mark.update)
    }
}
