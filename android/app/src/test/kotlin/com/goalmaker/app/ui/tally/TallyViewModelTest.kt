package com.goalmaker.app.ui.tally

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.core.content.edit
import com.goalmaker.app.application.planning.FakeUsageSource
import com.goalmaker.app.application.planning.NewRows
import com.goalmaker.app.application.planning.ProjectDraft
import com.goalmaker.app.application.planning.ProjectList
import com.goalmaker.app.application.planning.TallyDefaults
import com.goalmaker.app.application.planning.TallyFilter
import com.goalmaker.app.application.planning.TallyList
import com.goalmaker.app.application.planning.TallyRule
import com.goalmaker.app.application.planning.TallyRules
import com.goalmaker.app.application.planning.TallySample
import com.goalmaker.app.application.planning.TallyTotal
import com.goalmaker.app.application.planning.TallyTracker
import com.goalmaker.app.data.replica.TestReplica
import com.goalmaker.app.data.settings.SharedPreferencesSettingsStore
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The Tally place over a real replica (docs/tally.md, M8-13): the switch and usage access, today's
 * and the week's bars from every device's totals, the filters, and the owner's categories and rules.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TallyViewModelTest {
    private val usage = FakeUsageSource()
    private val now = Instant.parse("2026-09-30T12:00:00Z")
    private lateinit var test: TestReplica
    private lateinit var rows: NewRows
    private lateinit var settings: SharedPreferencesSettingsStore
    private lateinit var tally: TallyList
    private lateinit var projects: ProjectList
    private lateinit var tracker: TallyTracker
    private lateinit var defaults: TallyDefaults
    private lateinit var viewModel: TallyViewModel

    @Before
    fun setUp() {
        test = TestReplica()
        val application = RuntimeEnvironment.getApplication()
        val preferences = application.getSharedPreferences("tally-view-test", Context.MODE_PRIVATE)
        preferences.edit(commit = true) { clear() }
        settings = SharedPreferencesSettingsStore(preferences)
        rows = NewRows(test.catalog, { TestReplica.OWNER }, { now })
        tally = TallyList(test.replica, rows, {}) { PHONE }
        projects = ProjectList(test.replica, rows, {})
        defaults = TallyDefaults.load(application.assets.open("tally-rules.json"))
        tracker = TallyTracker(usage, tally, defaults.rules, settings, { now }, { ZoneOffset.UTC })
        usage.add("com.google.android.youtube", "2026-09-30T10:00:00Z", "2026-09-30T10:30:00Z")
        // Wednesday 30 September 2026 at noon: the week began on Monday the 28th.
        viewModel = TallyViewModel(tracker, tally, projects, defaults.categories, MutableStateFlow(4), Dispatchers.Unconfined) {
            LocalDateTime.parse("2026-09-30T12:00")
        }
    }

    @After
    fun tearDown() = test.close()

    @Test
    fun `Tally is off until turned on, and on without access the card asks for it`() {
        usage.granted = false
        viewModel = TallyViewModel(tracker, tally, projects, defaults.categories, MutableStateFlow(4), Dispatchers.Unconfined) {
            LocalDateTime.parse("2026-09-30T12:00")
        }
        assertEquals(TallyAccess(on = false, granted = false), viewModel.access.value)
        assertFalse(viewModel.access.value.askForAccess)

        viewModel.setOn(true)

        assertTrue(settings.tallyOn.value)
        assertTrue(viewModel.access.value.askForAccess)
        assertEquals(emptyList<Any>(), tally.totals(WEDNESDAY, WEDNESDAY))
    }

    @Test
    fun `access granted on the system's page is seen on return and counts at once`() {
        usage.granted = false
        viewModel = TallyViewModel(tracker, tally, projects, defaults.categories, MutableStateFlow(4), Dispatchers.Unconfined) {
            LocalDateTime.parse("2026-09-30T12:00")
        }
        viewModel.setOn(true)

        usage.granted = true
        viewModel.checkAccess()

        assertFalse(viewModel.access.value.askForAccess)
        assertEquals(listOf("video" to 30), tally.totals(WEDNESDAY, WEDNESDAY).map { it.category to it.minutes })
    }

    @Test
    fun `access taken away brings the card back, and turning off takes it down`() {
        viewModel.setOn(true)
        assertFalse(viewModel.access.value.askForAccess)

        usage.granted = false
        viewModel.checkAccess()
        assertTrue(viewModel.access.value.askForAccess)

        viewModel.setOn(false)
        assertFalse(viewModel.access.value.askForAccess)
        assertFalse(settings.tallyOn.value)
    }

    @Test
    fun `today and the week are stacked bars by category from every device's totals`() = runTest {
        val goalMaker = seedWeek()

        val state = viewModel.uiState.first { it.loaded && it.weekMinutes > 0 }

        val today = state.today!!
        assertEquals(120, today.minutes)
        assertEquals(
            listOf(TallySlice("coding", "Coding", "blue", 90), TallySlice("video", "Video", "red", 30)),
            today.slices,
        )
        assertEquals(listOf(70, 60, 120, 0, 0, 0, 0), state.week.map(TallyBar::minutes))
        assertEquals(MONDAY, state.week.first().day)
        assertEquals(listOf("video" to 50, "chat" to 20), state.week.first().slices.map { it.category to it.minutes })
        assertEquals(listOf("coding" to 150, "video" to 80, "chat" to 20), state.weekSlices.map { it.category to it.minutes })
        assertEquals(listOf(TallyProjectTime(goalMaker, "GoalMaker", 90)), state.projects)
        assertEquals(listOf("coding", "video", "chat"), state.chips.map(TallySlice::category))
    }

    @Test
    fun `the filters keep one kind of device or one category, and picking one again lets it go`() = runTest {
        seedWeek()
        viewModel.uiState.first { it.loaded && it.weekMinutes > 0 }

        viewModel.showKind(TallyRules.PHONE)
        shadowOf(Looper.getMainLooper()).idle()
        val phone = viewModel.uiState.first { it.filter == TallyFilter(kind = TallyRules.PHONE) }
        assertEquals(30, phone.today!!.minutes)
        assertEquals(100, phone.weekMinutes)
        assertEquals("the phone never links time to a project", emptyList<TallyProjectTime>(), phone.projects)
        assertEquals(listOf("video", "chat"), phone.chips.map(TallySlice::category))

        viewModel.showCategory("video")
        shadowOf(Looper.getMainLooper()).idle()
        val video = viewModel.uiState.first { it.filter.category == "video" }
        assertEquals(listOf(50, 0, 30, 0, 0, 0, 0), video.week.map(TallyBar::minutes))

        viewModel.showKind(TallyRules.PHONE)
        viewModel.showCategory("coding")
        shadowOf(Looper.getMainLooper()).idle()
        val coding = viewModel.uiState.first { it.filter == TallyFilter(category = "coding") }
        assertEquals(listOf(0, 60, 90, 0, 0, 0, 0), coding.week.map(TallyBar::minutes))
        assertEquals(90, coding.projects.single().minutes)

        viewModel.showKind(TallyRules.PC)
        viewModel.showCategory("coding")
        shadowOf(Looper.getMainLooper()).idle()
        val pc = viewModel.uiState.first { it.filter == TallyFilter(kind = TallyRules.PC) }
        assertEquals(150, pc.weekMinutes)
        assertEquals(listOf("coding"), pc.chips.map(TallySlice::category))
    }

    @Test
    fun `a rule added here comes first and sorts the next day, on the other app too`() = runTest {
        val chess = viewModel.addCategory(" Chess ", "teal", "♟")!!
        val before = TallySample(TallyRules.WINDOWS, "firefox.exe", "Chess openings - YouTube - Mozilla Firefox")
        assertEquals("video", TallyRules.sortSample(before, tally.rules(), defaults.rules).category)

        assertNotNull(viewModel.addRule(TallyRule(TallyRules.TITLE, "chess openings", TallyRules.WINDOWS, chess.id)))
        assertNotNull(viewModel.addRule(TallyRule(TallyRules.APP, "org.lichess.mobileapp", TallyRules.ANY, chess.id)))

        // The PC reads the synced rules before its own shipped ones, so the same window is Chess now.
        assertEquals(chess.id, TallyRules.sortSample(before, tally.rules(), defaults.rules).category)
        assertEquals(chess.id, TallyRules.sortSample(TallySample(TallyRules.ANDROID, "org.lichess.mobileapp"), tally.rules(), defaults.rules).category)

        tally.rewrite(WEDNESDAY, listOf(TallyTotal(WEDNESDAY, chess.id, null, 25)))
        val state = viewModel.uiState.first { it.loaded && it.rules.size == 2 && (it.today?.minutes ?: 0) > 0 }
        assertEquals(listOf(TallySlice(chess.id, "Chess", "teal", 25)), state.today!!.slices)
        assertEquals("Chess", state.nameOf(state.rules.first().category))
        assertEquals(listOf("Chess"), state.own.map { it.name })
        assertEquals("the owner's categories come before the shipped ones", "Chess", state.categories.first().name)
    }

    @Test
    fun `editing a category and a rule changes them in place, and a deleted category's time is a removed one`() = runTest {
        val chess = viewModel.addCategory("Chess", "teal", "")!!
        val rule = viewModel.addRule(TallyRule(TallyRules.APP, "org.lichess.mobileapp", TallyRules.ANY, chess.id))!!
        tally.rewrite(WEDNESDAY, listOf(TallyTotal(WEDNESDAY, chess.id, null, 25)))

        assertTrue(viewModel.updateCategory(chess.id, "Board games", "green", "🎲"))
        assertTrue(viewModel.updateRule(rule.id!!, TallyRule(TallyRules.TITLE, "lichess", TallyRules.WINDOWS, chess.id)))
        shadowOf(Looper.getMainLooper()).idle()

        val edited = viewModel.uiState.first { state -> state.own.any { it.name == "Board games" } && state.rules.any { it.match == TallyRules.TITLE } }
        assertEquals(listOf(TallySlice(chess.id, "Board games", "green", 25)), edited.today!!.slices)
        assertEquals(listOf(TallyRule(TallyRules.TITLE, "lichess", TallyRules.WINDOWS, chess.id, id = rule.id)), edited.rules)

        viewModel.deleteCategory(chess.id)
        viewModel.deleteRule(rule.id!!)
        shadowOf(Looper.getMainLooper()).idle()

        val deleted = viewModel.uiState.first { it.own.isEmpty() && it.rules.isEmpty() }
        assertEquals("the time stays, with no name, so the place calls it a removed category", "", deleted.today!!.slices.single().name)
        assertEquals(25, deleted.today!!.minutes)
    }

    /** Monday and Wednesday on this phone, Tuesday and Wednesday on the PC (one project on Wednesday). */
    private fun seedWeek(): String {
        tally.rewrite(MONDAY, listOf(TallyTotal(MONDAY, "video", null, 50), TallyTotal(MONDAY, "chat", null, 20)))
        tally.rewrite(WEDNESDAY, listOf(TallyTotal(WEDNESDAY, "video", null, 30)))
        val goalMaker = projects.add(ProjectDraft(name = "GoalMaker"))!!.id
        pc(MONDAY.plusDays(1), "coding", null, 60)
        pc(WEDNESDAY, "coding", goalMaker, 90)
        // Last week's minutes stay out of this week.
        pc(MONDAY.minusDays(1), "games", null, 200)
        return goalMaker
    }

    private fun pc(day: LocalDate, category: String, project: String?, minutes: Int) {
        val row = rows.create(
            "tally_days",
            mapOf(
                "id" to JsonPrimitive(TallyRules.dayId(TestReplica.OWNER, day, PC, category, project)),
                "day" to JsonPrimitive(day.toString()),
                "device" to JsonPrimitive(PC),
                "device_kind" to JsonPrimitive(TallyRules.PC),
                "category" to JsonPrimitive(category),
                "project_id" to (project?.let(::JsonPrimitive) ?: JsonNull),
                "minutes" to JsonPrimitive(minutes),
            ),
        )!!
        test.replica.queue("tally_days", row)
    }

    private companion object {
        const val PHONE = "d1e57000-0000-4000-8000-00000000aaaa"
        const val PC = "d1e57000-0000-4000-8000-00000000bbbb"
        val MONDAY: LocalDate = LocalDate.parse("2026-09-28")
        val WEDNESDAY: LocalDate = LocalDate.parse("2026-09-30")
    }
}
