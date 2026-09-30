package com.goalmaker.app.application.planning

import android.app.Application
import com.goalmaker.app.data.replica.TestReplica
import com.goalmaker.app.data.replica.text
import java.time.Instant
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Tally's days, categories and rules on a real replica (docs/tally.md, M8-10). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TallyListTest {
    private lateinit var test: TestReplica
    private lateinit var rows: NewRows
    private lateinit var tally: TallyList
    private var device = PHONE
    private val day = LocalDate.parse("2026-09-28")

    @Before
    fun setUp() {
        test = TestReplica()
        rows = NewRows(test.catalog, { TestReplica.OWNER }, { Instant.parse("2026-09-28T12:00:00Z") })
        tally = TallyList(test.replica, rows, {}) { device }
    }

    @After
    fun tearDown() = test.close()

    @Test
    fun `a rewritten day keeps one row per category with the contract's id`() {
        tally.rewrite(
            day,
            listOf(
                TallyTotal(day, "video", null, 50),
                TallyTotal(day, "chat", null, 20),
                TallyTotal(day.plusDays(1), "games", null, 30),
            ),
        )

        val totals = tally.totals(day, day)
        assertEquals(listOf("chat" to 20, "video" to 50), totals.map { it.category to it.minutes })
        assertEquals(TallyRules.dayId(TestReplica.OWNER, day, PHONE, "video", null), totals.last().id)
        assertEquals(TallyRules.PHONE, totals.last().deviceKind)
        assertEquals(PHONE, totals.last().device)
        assertEquals("another day's totals wait for that day", emptyList<TallyDay>(), tally.totals(day.plusDays(1), day.plusDays(1)))
    }

    @Test
    fun `rewriting a day replaces this phone's rows and leaves the other devices' alone`() {
        tally.rewrite(day, listOf(TallyTotal(day, "video", null, 50), TallyTotal(day, "chat", null, 20)))
        device = PC
        tally.rewrite(day, listOf(TallyTotal(day, "coding", null, 90)))
        device = PHONE

        tally.rewrite(day, listOf(TallyTotal(day, "video", null, 80)))

        assertEquals(
            listOf(PHONE to "video", PC to "coding"),
            tally.totals(day, day).map { it.device to it.category },
        )
        assertEquals(80, tally.totals(day, day).first().minutes)
        val chat = test.replica.get("tally_days", TallyRules.dayId(TestReplica.OWNER, day, PHONE, "chat", null))!!
        assertNotNull("the dropped category is a tombstone, so the server hears of it", chat.text("deleted_at"))

        tally.rewrite(day, listOf(TallyTotal(day, "chat", null, 5)))
        assertNull("a category back on a later rewrite comes back", test.replica.get("tally_days", chat.text("id")!!)!!.text("deleted_at"))
    }

    @Test
    fun `the phone adds a day's projects into their category and holds a day at most`() {
        tally.rewrite(day, listOf(TallyTotal(day, "coding", "p-one", 1000), TallyTotal(day, "coding", null, 1000)))

        val coding = tally.totals(day, day).single()
        assertNull(coding.project)
        assertEquals(TallyRules.MAX_MINUTES, coding.minutes)
    }

    @Test
    fun `a range of days reads every day in it, both ends included`() {
        (0L..3L).forEach { offset -> tally.rewrite(day.plusDays(offset), listOf(TallyTotal(day.plusDays(offset), "reading", null, 10))) }

        assertEquals(
            listOf(day.plusDays(1), day.plusDays(2)),
            tally.totals(day.plusDays(1), day.plusDays(2)).map(TallyDay::day),
        )
    }

    @Test
    fun `nobody signed in writes nothing`() {
        val signedOut = TallyList(test.replica, NewRows(test.catalog, { null }, Instant::now), {}) { device }

        assertFalse(signedOut.rewrite(day, listOf(TallyTotal(day, "video", null, 50))))
        assertEquals(0, test.replica.outbox().size)
    }

    @Test
    fun `the owner's categories and rules come back in the order they were added`() {
        val chess = tally.addCategory(" Chess ", "teal", "♟")!!
        assertNotNull(tally.addCategory("Music", "pink"))
        assertNull(tally.addCategory("  ", "teal"))
        assertNull(tally.addCategory("Maps", "Not a color"))

        assertEquals(listOf("Chess", "Music"), tally.categories().map(TallyCategory::name))
        assertEquals(0, chess.position)

        val lesson = tally.addRule(TallyRule(TallyRules.TITLE, " chess lesson ", TallyRules.WINDOWS, chess.id))!!
        assertNotNull(tally.addRule(TallyRule(TallyRules.APP, "com.chess", TallyRules.ANDROID, chess.id, project = "p-chess")))
        assertNull(tally.addRule(TallyRule(TallyRules.TITLE, "chess", TallyRules.ANDROID, chess.id)))
        assertNull(tally.addRule(TallyRule("window", "chess", TallyRules.ANY, chess.id)))
        assertNull(tally.addRule(TallyRule(TallyRules.APP, " ", TallyRules.ANY, chess.id)))

        assertEquals("chess lesson", lesson.pattern)
        assertEquals(listOf("chess lesson", "com.chess"), tally.rules().map(TallyRule::pattern))
        assertEquals("p-chess", tally.rules().last().project)
        val sorted = TallyRules.sortSample(TallySample(TallyRules.ANDROID, "com.chess"), tally.rules(), emptyList())
        assertEquals(chess.id, sorted.category)
    }

    @Test
    fun `the app ships the Tally defaults`() {
        val defaults = TallyDefaults.load(RuntimeEnvironment.getApplication().assets.open("tally-rules.json"))

        assertEquals("coding", defaults.categories.first().id)
        assertEquals(
            TallySort("video", null),
            TallyRules.sortSample(TallySample(TallyRules.ANDROID, "com.google.android.youtube"), emptyList(), defaults.rules),
        )
    }

    private companion object {
        const val PHONE = "d1e57000-0000-4000-8000-00000000aaaa"
        const val PC = "d1e57000-0000-4000-8000-00000000bbbb"
    }
}
