package com.goalmaker.app.ui.review

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.AreaList
import com.goalmaker.app.application.planning.GoalList
import com.goalmaker.app.application.planning.HabitList
import com.goalmaker.app.application.planning.NewRows
import com.goalmaker.app.application.planning.ProjectList
import com.goalmaker.app.application.planning.Reflection
import com.goalmaker.app.application.planning.ReviewList
import com.goalmaker.app.application.planning.ReviewRules
import com.goalmaker.app.application.planning.RitualRunList
import com.goalmaker.app.application.planning.TagList
import com.goalmaker.app.application.planning.TaskList
import com.goalmaker.app.data.replica.TestReplica
import com.goalmaker.app.domain.planning.PromptLibrary
import com.goalmaker.app.ui.ScreenTheme
import java.io.File
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The guided review as the owner walks it (docs/reviews.md): a review written before, with a letter,
 * answers and ratings, still runs to its end and closes from there.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h914dp")
class ReviewScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var test: TestReplica
    private lateinit var reviews: ReviewList
    private lateinit var tasks: TaskList
    private lateinit var areas: AreaList
    private lateinit var goals: GoalList
    private lateinit var habits: HabitList
    private lateinit var rituals: RitualRunList
    private lateinit var prompts: PromptLibrary
    private val context get() = RuntimeEnvironment.getApplication()

    // Monday 21 September 2026: the week under review is 14 to 20 September.
    private val today = LocalDate.parse("2026-09-21")
    private val weekStart = LocalDate.parse("2026-09-14")

    @Before
    fun setUp() {
        test = TestReplica()
        val rows = NewRows(test.catalog, { TestReplica.OWNER }, { Instant.parse("2026-09-21T09:00:00Z") })
        areas = AreaList(test.replica, rows, listOf("violet"), {})
        tasks = TaskList(test.replica, rows, areas, TagList(test.replica, rows, {}), ProjectList(test.replica, rows, {}), {}) { today }
        goals = GoalList(test.replica, rows, {})
        habits = HabitList(test.replica, rows, {})
        reviews = ReviewList(test.replica, rows, {})
        rituals = RitualRunList(test.replica, rows, {})
        prompts = PromptLibrary.parse(File(System.getProperty("goalmaker.contracts")!!).resolve("content/prompts.json").readText())
    }

    @After
    fun tearDown() = test.close()

    private fun viewModel() = ReviewViewModel(
        kind = ReviewRules.WEEKLY,
        periodStart = weekStart,
        reviews = reviews,
        tasks = tasks,
        areas = areas,
        goals = goals,
        habits = habits,
        prompts = prompts,
        rituals = rituals,
        io = Dispatchers.Unconfined,
        today = { today },
    )

    @Test
    fun `a review written before runs to its end, keeps an edit and closes`() {
        val review = reviews.open(ReviewRules.WEEKLY, weekStart)!!
        reviews.setSummary(review.id, "## What went well\nThe dentist, finally.")
        reviews.setReflections(review.id, listOf(Reflection("weekly/win", "A calm week.")))
        reviews.setMood(review.id, 4)
        reviews.setEnergy(review.id, 3)
        // It was finished once already, so the ritual for today is recorded.
        rituals.record(RitualRunList.WEEKLY_REVIEW, today)
        var closed = 0
        val model = viewModel()
        compose.setContent { ScreenTheme { ReviewScreen(viewModel = model, onClose = { closed++ }) } }

        compose.onNodeWithText(context.getString(R.string.reviews_continue)).performClick()
        repeat(2) { compose.onNodeWithText(context.getString(R.string.reviews_next)).performClick() }
        compose.onNodeWithText("A calm week.").performTextReplacement("A calmer week.")
        repeat(2) { compose.onNodeWithText(context.getString(R.string.reviews_next)).performClick() }
        compose.onNodeWithText(context.getString(R.string.reviews_finish)).performClick()
        compose.onNodeWithText(context.getString(R.string.reviews_done_title)).assertExists()
        compose.onNodeWithText(context.getString(R.string.reviews_close)).performClick()

        assertEquals(1, closed)
        assertEquals("A calmer week.", reviews.find(ReviewRules.WEEKLY, weekStart)!!.reflections.single().answer)
        assertTrue(today in rituals.ran(RitualRunList.WEEKLY_REVIEW))
    }
}
