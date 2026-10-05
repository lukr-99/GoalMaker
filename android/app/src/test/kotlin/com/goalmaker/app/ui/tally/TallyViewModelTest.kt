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

        val today = state.day!!
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
        assertEquals(30, phone.day!!.minutes)
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
        val state = viewModel.uiState.first { it.loaded && it.rules.size == 2 && (it.day?.minutes ?: 0) > 0 }
        assertEquals(listOf(TallySlice(chess.id, "Chess", "teal", 25)), state.day!!.slices)
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
        assertEquals(listOf(TallySlice(chess.id, "Board games", "green", 25)), edited.day!!.slices)
        assertEquals(listOf(TallyRule(TallyRules.TITLE, "lichess", TallyRules.WINDOWS, chess.id, id = rule.id)), edited.rules)

        viewModel.deleteCategory(chess.id)
        viewModel.deleteRule(rule.id!!)
        shadowOf(Looper.getMainLooper()).idle()

        val deleted = viewModel.uiState.first { it.own.isEmpty() && it.rules.isEmpty() }
        assertEquals("the time stays, with no name, so the place calls it a removed category", "", deleted.day!!.slices.single().name)
        assertEquals(25, deleted.day!!.minutes)
    }

    @Test
    fun `a day of the week shows closer up, and picking it again goes back to today`() = runTest {
        seedWeek()
        viewModel.uiState.first { it.loaded && it.weekMinutes > 0 }

        viewModel.showDay(MONDAY)
        shadowOf(Looper.getMainLooper()).idle()
        val monday = viewModel.uiState.first { it.day?.day == MONDAY }
        assertFalse(monday.isToday)
        assertEquals(0, monday.dayIndex)
        assertEquals(70, monday.day!!.minutes)
        assertEquals(listOf("video" to 50, "chat" to 20), monday.day!!.slices.map { it.category to it.minutes })

        viewModel.showDay(MONDAY)
        shadowOf(Looper.getMainLooper()).idle()
        val back = viewModel.uiState.first { it.day?.day == WEDNESDAY }
        assertTrue(back.isToday)
        assertEquals(2, back.dayIndex)
    }

    @Test
    fun `this phone's own hours and apps show once it counts, and the chips narrow them`() = runTest {
        usage.names["com.google.android.youtube"] = "YouTube"
        usage.add("com.whatsapp", "2026-09-30T11:00:00Z", "2026-09-30T11:10:00Z")
        usage.add("com.google.android.youtube", "2026-09-28T09:00:00Z", "2026-09-28T09:15:00Z")
        assertFalse("off, the phone shows none of its own", viewModel.uiState.first { it.loaded }.counting)

        viewModel.setOn(true)
        shadowOf(Looper.getMainLooper()).idle()

        val state = viewModel.uiState.first { it.counting && it.apps.isNotEmpty() }
        assertEquals(listOf("video" to 30, "chat" to 10), state.apps.map { it.category to it.minutes })
        assertEquals(TallyAppRow("com.google.android.youtube", "YouTube", 30), state.apps.first().apps.single())
        assertEquals("a name the phone doesn't know is the package", TallyAppRow("com.whatsapp", "com.whatsapp", 10), state.apps.last().apps.single())
        assertEquals(24, state.hours.size)
        assertEquals(4, state.hours.first().hour)
        assertEquals(
            listOf(10 to listOf("video" to 1800), 11 to listOf("chat" to 600)),
            state.hours.filter { it.seconds > 0 }.map { hour -> hour.hour to hour.parts.map { it.category to it.seconds } },
        )

        viewModel.showApps(TallyAppScope.WEEK)
        shadowOf(Looper.getMainLooper()).idle()
        val week = viewModel.uiState.first { it.appScope == TallyAppScope.WEEK }
        assertEquals(listOf("video" to 45, "chat" to 10), week.apps.map { it.category to it.minutes })

        viewModel.showCategory("chat")
        shadowOf(Looper.getMainLooper()).idle()
        val chat = viewModel.uiState.first { it.filter.category == "chat" }
        assertEquals(listOf("chat"), chat.apps.map(TallyAppGroup::category))
        assertEquals(600, chat.hours.sumOf(TallyHourBar::seconds))

        viewModel.showKind(TallyRules.PC)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("the PC's apps stay on the PC", viewModel.uiState.first { it.filter.kind == TallyRules.PC }.otherDevice)
    }

    @Test
    fun `make a rule under an app sorts it again at once`() = runTest {
        viewModel.setOn(true)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("video" to 30), tally.totals(WEDNESDAY, WEDNESDAY).map { it.category to it.minutes })

        val rule = viewModel.ruleFor("com.google.android.youtube", "video")
        assertEquals(TallyRule(TallyRules.APP, "com.google.android.youtube", TallyRules.ANDROID, "video"), rule)
        assertNotNull(viewModel.addRule(rule.copy(category = "study")))

        assertEquals(listOf("study" to 30), tally.totals(WEDNESDAY, WEDNESDAY).map { it.category to it.minutes })
        val state = viewModel.uiState.first { it.apps.any { group -> group.category == "study" } }
        assertEquals(listOf("study"), state.apps.map(TallyAppGroup::category))
    }

    @Test
    fun `the last 8 weeks and each device's week are stacked bars by category`() = runTest {
        seedWeek()

        val state = viewModel.uiState.first { it.loaded && it.weekMinutes > 0 }

        // Eight weeks, oldest first: last week's 200 games minutes, then this week's 250 (100 on the phone, 150 on the PC).
        assertEquals(8, state.trend.size)
        assertEquals(listOf(0, 0, 0, 0, 0, 0, 200, 250), state.trend.map(TallyBar::minutes))
        assertEquals(listOf(TallyRules.PHONE, TallyRules.PC), state.devices.map(TallyDeviceTime::kind))
        assertEquals(listOf(100, 150), state.devices.map { it.bar.minutes })
        assertEquals(listOf("coding" to 150), state.devices.last().bar.slices.map { it.category to it.minutes })
    }

    @Test
    fun `an app in Other is left to sort, and moving it sorts it at once with one rule`() = runTest {
        viewModel.setOn(true)
        shadowOf(Looper.getMainLooper()).idle()
        viewModel.moveApp("com.google.android.youtube", TallyRules.OTHER)
        shadowOf(Looper.getMainLooper()).idle()
        var state = viewModel.uiState.first { it.toSort.isNotEmpty() }
        assertEquals(listOf("com.google.android.youtube"), state.toSort.map(TallyAppRow::app))

        viewModel.moveApp("com.google.android.youtube", "study")
        shadowOf(Looper.getMainLooper()).idle()

        state = viewModel.uiState.first { it.toSort.isEmpty() && it.apps.any { group -> group.category == "study" } }
        assertEquals(listOf("study" to 30), tally.totals(WEDNESDAY, WEDNESDAY).map { it.category to it.minutes })
        assertEquals(1, tally.rules().size)
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
