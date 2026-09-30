package com.goalmaker.app.ui.stats

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import com.goalmaker.app.application.planning.AreaList
import com.goalmaker.app.application.planning.GoalList
import com.goalmaker.app.application.planning.HabitList
import com.goalmaker.app.application.planning.NewRows
import com.goalmaker.app.application.planning.ProjectList
import com.goalmaker.app.application.planning.ReviewList
import com.goalmaker.app.application.planning.TagList
import com.goalmaker.app.application.planning.TallyDefaults
import com.goalmaker.app.application.planning.TallyList
import com.goalmaker.app.application.planning.TallyTotal
import com.goalmaker.app.application.planning.TaskList
import com.goalmaker.app.application.planning.WantList
import com.goalmaker.app.data.replica.TestReplica
import com.goalmaker.app.data.settings.SharedPreferencesSettingsStore
import com.goalmaker.app.ui.tally.TallyBar
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The stats screen's Tally block over a real replica: twelve weeks, shown only with Tally time (M8-13). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class StatsViewModelTest {
    private lateinit var test: TestReplica
    private lateinit var tally: TallyList
    private lateinit var viewModel: StatsViewModel

    // Wednesday 30 September 2026 at noon; the twelve weeks run from Monday 13 July.
    private val today = LocalDate.parse("2026-09-30")

    @Before
    fun setUp() {
        test = TestReplica()
        val application = RuntimeEnvironment.getApplication()
        val preferences = application.getSharedPreferences("stats-view-test", Context.MODE_PRIVATE)
        preferences.edit(commit = true) { clear() }
        val settings = SharedPreferencesSettingsStore(preferences)
        val rows = NewRows(test.catalog, { TestReplica.OWNER }, { Instant.parse("2026-09-30T10:00:00Z") })
        val areas = AreaList(test.replica, rows, listOf("violet", "blue"), {})
        val tasks = TaskList(test.replica, rows, areas, TagList(test.replica, rows, {}), ProjectList(test.replica, rows, {}), {}) { today }
        tally = TallyList(test.replica, rows, {}) { "phone" }
        viewModel = StatsViewModel(
            tasks = tasks,
            goals = GoalList(test.replica, rows, {}),
            habits = HabitList(test.replica, rows, {}),
            reviews = ReviewList(test.replica, rows, {}),
            wants = WantList(test.replica, rows, {}) { today },
            tally = tally,
            tallyCategories = TallyDefaults.load(application.assets.open("tally-rules.json")).categories,
            settings = settings,
            io = Dispatchers.Unconfined,
            clock = { LocalDateTime.parse("2026-09-30T12:00") },
        )
    }

    @After
    fun tearDown() = test.close()

    @Test
    fun `no Tally time, no Tally block`() = runTest {
        val state = viewModel.uiState.first { it.loaded }

        assertTrue(state.tallyWeeks.isEmpty())
    }

    @Test
    fun `the Tally block is twelve weeks, oldest first, with each category's minutes`() = runTest {
        val thisWeek = LocalDate.parse("2026-09-29")
        val firstWeek = LocalDate.parse("2026-07-14")
        tally.rewrite(thisWeek, listOf(TallyTotal(thisWeek, "video", null, 40), TallyTotal(thisWeek, "chat", null, 50)))
        tally.rewrite(firstWeek, listOf(TallyTotal(firstWeek, "reading", null, 30)))
        // Thirteen weeks back is left out.
        tally.rewrite(firstWeek.minusWeeks(1), listOf(TallyTotal(firstWeek.minusWeeks(1), "games", null, 300)))

        val state = viewModel.uiState.first { it.loaded && it.tallyWeeks.isNotEmpty() }

        assertEquals(12, state.tallyWeeks.size)
        assertEquals(LocalDate.parse("2026-07-13"), state.tallyWeeks.first().day)
        assertEquals(LocalDate.parse("2026-09-28"), state.tallyWeeks.last().day)
        assertEquals(listOf(30) + List(10) { 0 } + 90, state.tallyWeeks.map(TallyBar::minutes))
        assertEquals(listOf("chat" to 50, "video" to 40), state.tallyWeeks.last().slices.map { it.category to it.minutes })
        assertEquals(listOf("Chat", "Video"), state.tallyWeeks.last().slices.map { it.name })
        assertEquals(listOf("chat" to 50, "video" to 40, "reading" to 30), state.tallySlices.map { it.category to it.minutes })
    }
}
