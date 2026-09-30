package com.goalmaker.app.application.planning

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import com.goalmaker.app.data.replica.TestReplica
import com.goalmaker.app.data.settings.SharedPreferencesSettingsStore
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The phone's usage history into daily totals, over a fake history and a real replica (docs/tally.md, M8-11). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TallyTrackerTest {
    private lateinit var test: TestReplica
    private lateinit var tally: TallyList
    private lateinit var settings: SharedPreferencesSettingsStore
    private lateinit var tracker: TallyTracker
    private val usage = FakeUsageSource()
    private var now = Instant.parse("2026-09-28T12:00:00Z")
    private var owner: String? = TestReplica.OWNER

    @Before
    fun setUp() {
        test = TestReplica()
        val application = RuntimeEnvironment.getApplication()
        val preferences = application.getSharedPreferences("tally-test", Context.MODE_PRIVATE)
        preferences.edit(commit = true) { clear() }
        settings = SharedPreferencesSettingsStore(preferences)
        tally = TallyList(test.replica, NewRows(test.catalog, { owner }, { now }), {}) { PHONE }
        val defaults = TallyDefaults.load(application.assets.open("tally-rules.json"))
        tracker = TallyTracker(usage, tally, defaults.rules, settings, { now }, { ZoneOffset.UTC })
        tracker.turn(true)
    }

    @After
    fun tearDown() = test.close()

    @Test
    fun `apps in front become minutes per category on the planning day`() {
        settings.setTallyReadUntil(Instant.parse("2026-09-28T08:00:00Z"))
        usage.add(YOUTUBE, "2026-09-28T10:00:00Z", "2026-09-28T10:50:00Z")
        usage.add("com.whatsapp", "2026-09-28T11:00:00Z", "2026-09-28T11:20:00Z")
        usage.add("com.example.maps", "2026-09-28T11:30:00Z", "2026-09-28T11:35:00Z")

        assertEquals(listOf(day), tracker.track())

        assertEquals(listOf("chat" to 20, "other" to 5, "video" to 50), minutes(day))
        assertEquals(now, settings.tallyReadUntil())
    }

    @Test
    fun `time across the 04 00 rollover goes to both planning days`() {
        now = Instant.parse("2026-09-28T05:00:00Z")
        settings.setTallyReadUntil(Instant.parse("2026-09-27T22:00:00Z"))
        usage.add(YOUTUBE, "2026-09-28T03:30:00Z", "2026-09-28T04:30:00Z")

        assertEquals(listOf(day.minusDays(1), day), tracker.track())

        assertEquals(listOf("video" to 30), minutes(day.minusDays(1)))
        assertEquals(listOf("video" to 30), minutes(day))
        assertEquals(Instant.parse("2026-09-27T04:00:00Z"), usage.reads.single().first)
    }

    @Test
    fun `each run counts the watermark's day again from its start, so nothing is added twice`() {
        settings.setTallyReadUntil(Instant.parse("2026-09-28T08:00:00Z"))
        usage.add(YOUTUBE, "2026-09-28T10:00:00Z", "2026-09-28T10:50:00Z")
        tracker.track()

        now = Instant.parse("2026-09-28T14:00:00Z")
        usage.add(YOUTUBE, "2026-09-28T13:00:00Z", "2026-09-28T13:10:00Z")
        assertEquals(listOf(day), tracker.track())
        assertEquals(Instant.parse("2026-09-28T04:00:00Z"), usage.reads.last().first)
        assertEquals(listOf("video" to 60), minutes(day))

        // The next morning, the day the last run ended in is read whole once more, then today.
        now = Instant.parse("2026-09-29T05:00:00Z")
        usage.add(YOUTUBE, "2026-09-29T04:10:00Z", "2026-09-29T04:20:00Z")
        assertEquals(listOf(day, day.plusDays(1)), tracker.track())
        assertEquals(Instant.parse("2026-09-28T04:00:00Z"), usage.reads.last().first)
        assertEquals(listOf("video" to 60), minutes(day))
        assertEquals(listOf("video" to 10), minutes(day.plusDays(1)))
    }

    @Test
    fun `a first run reads every whole day the phone still keeps`() {
        usage.add(YOUTUBE, "2026-09-22T10:00:00Z", "2026-09-22T10:15:00Z")

        val days = tracker.track()

        assertEquals((0L..6L).map { LocalDate.parse("2026-09-22").plusDays(it) }, days)
        assertEquals(Instant.parse("2026-09-22T04:00:00Z"), usage.reads.single().first)
        assertEquals(listOf("video" to 15), minutes(LocalDate.parse("2026-09-22")))
    }

    @Test
    fun `access taken away writes nothing and keeps the watermark`() {
        settings.setTallyReadUntil(Instant.parse("2026-09-28T08:00:00Z"))
        usage.add(YOUTUBE, "2026-09-28T10:00:00Z", "2026-09-28T10:50:00Z")
        tracker.track()
        val outbox = test.replica.outbox().size

        usage.granted = false
        now = Instant.parse("2026-09-28T14:00:00Z")
        usage.add(YOUTUBE, "2026-09-28T13:00:00Z", "2026-09-28T13:30:00Z")

        assertEquals(emptyList<LocalDate>(), tracker.track())
        assertEquals(listOf("video" to 50), minutes(day))
        assertEquals(outbox, test.replica.outbox().size)
        assertEquals(Instant.parse("2026-09-28T12:00:00Z"), settings.tallyReadUntil())
    }

    @Test
    fun `off or signed out reads and writes nothing, and turning off forgets the watermark`() {
        usage.add(YOUTUBE, "2026-09-28T10:00:00Z", "2026-09-28T10:50:00Z")
        owner = null
        assertEquals(emptyList<LocalDate>(), tracker.track())
        assertNull(settings.tallyReadUntil())
        assertEquals(0, test.replica.outbox().size)

        owner = TestReplica.OWNER
        tracker.track()
        tracker.turn(false)
        val reads = usage.reads.size

        assertEquals(emptyList<LocalDate>(), tracker.track())
        assertEquals(reads, usage.reads.size)
        assertNull(settings.tallyReadUntil())
    }

    @Test
    fun `the owner's rules come before the shipped ones, and the phone never names a project`() {
        settings.setTallyReadUntil(Instant.parse("2026-09-28T08:00:00Z"))
        tally.addRule(TallyRule(TallyRules.APP, YOUTUBE, TallyRules.ANDROID, "study", project = "p-lectures"))
        usage.add(YOUTUBE, "2026-09-28T10:00:00Z", "2026-09-28T10:50:00Z")

        tracker.track()

        assertEquals(listOf("study" to 50), minutes(day))
        assertNull(tally.totals(day, day).single().project)
    }

    private fun minutes(day: LocalDate) = tally.totals(day, day).map { it.category to it.minutes }

    private companion object {
        const val PHONE = "d1e57000-0000-4000-8000-00000000aaaa"
        const val YOUTUBE = "com.google.android.youtube"
        val day: LocalDate = LocalDate.parse("2026-09-28")
    }
}
