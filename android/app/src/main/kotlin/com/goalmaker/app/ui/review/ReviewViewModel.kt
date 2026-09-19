package com.goalmaker.app.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goalmaker.app.application.planning.AreaList
import com.goalmaker.app.application.planning.GoalDraft
import com.goalmaker.app.application.planning.GoalList
import com.goalmaker.app.application.planning.GoalRules
import com.goalmaker.app.application.planning.HabitList
import com.goalmaker.app.application.planning.PlanDecision
import com.goalmaker.app.application.planning.Reflection
import com.goalmaker.app.application.planning.ReviewItem
import com.goalmaker.app.application.planning.ReviewList
import com.goalmaker.app.application.planning.ReviewLookBack
import com.goalmaker.app.application.planning.RitualRunList
import com.goalmaker.app.application.planning.TaskItem
import com.goalmaker.app.application.planning.TaskList
import com.goalmaker.app.domain.planning.PromptLibrary
import com.goalmaker.app.domain.planning.PromptRules
import com.goalmaker.app.domain.planning.ReviewQuestion
import com.goalmaker.app.ui.goals.GoalBoard
import java.time.LocalDate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The guided review of one period (docs/reviews.md, spec stories 59 to 64): look back, handle what is
 * still open, reflect on the prompts the library and the period's facts give, rate mood and energy,
 * and set the next period's goals. Everything is saved as it is answered.
 */
class ReviewViewModel(
    private val kind: String,
    private val periodStart: LocalDate,
    private val reviews: ReviewList,
    private val tasks: TaskList,
    private val areas: AreaList,
    private val goals: GoalList,
    private val habits: HabitList,
    private val prompts: PromptLibrary,
    private val rituals: RitualRunList,
    private val io: CoroutineDispatcher,
    private val today: () -> LocalDate,
) : ViewModel() {
    private val step = MutableStateFlow(ReviewStep.LOOK_BACK)
    private val questions = MutableStateFlow<List<ReviewQuestion>>(emptyList())
    private val answers = MutableStateFlow<Map<String, String>>(emptyMap())
    private val opened = MutableStateFlow<ReviewItem?>(null)

    val uiState: StateFlow<ReviewUiState> = combine(
        reviews.watch().flowOn(io),
        tasks.watchAll().flowOn(io),
        goals.watch().flowOn(io),
        habits.watch().flowOn(io),
        combine(step, questions, answers, opened) { current, asked, written, review -> Screen(current, asked, written, review) },
    ) { allReviews, taskList, (goalList, entries), habitData, screen ->
        val day = today()
        val digest = ReviewLookBack.build(kind, periodStart, taskList, areas.all(), goalList, entries, habitData, day)
        val nextStart = ReviewLookBack.periodEnd(kind, periodStart).plusDays(1)
        val horizon = ReviewLookBack.horizonOf(kind)
        val board = GoalBoard.build(goalList, entries, taskList, day, habits = habitData)
        val next = board.sections.firstOrNull { it.horizon == horizon && it.start == nextStart }
        ReviewUiState(
            loaded = true,
            step = screen.step,
            kind = kind,
            digest = digest,
            review = allReviews.firstOrNull { it.kind == kind && it.periodStart == periodStart } ?: screen.review,
            questions = screen.questions,
            answers = screen.answers,
            nextGoals = next?.rows.orEmpty(),
            canCopyGoals = next?.canCopy ?: goalList.none { it.horizon == horizon && it.periodStart == nextStart },
            goals = goalList,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReviewUiState(kind = kind))

    init {
        viewModelScope.launch(io) {
            val review = reviews.open(kind, periodStart)
            opened.value = review
            answers.value = review?.reflections.orEmpty().associate { it.promptId to it.answer }
            questions.value = ask(review)
        }
    }

    /** The prompts this review asks: the ones the period calls for, then the rotation (docs/reviews.md). */
    private fun ask(review: ReviewItem?): List<ReviewQuestion> {
        val already = review?.reflections.orEmpty()
        if (already.isNotEmpty()) {
            return already.map { reflection ->
                val prompt = prompts[reflection.promptId]
                ReviewQuestion(
                    reflection.promptId,
                    prompt?.let { PromptRules.text(it, kind) } ?: reflection.promptId,
                )
            }
        }
        val digest = ReviewLookBack.build(kind, periodStart, tasks.all(), areas.all(), goals.all(), goals.entries(), habits.read(), today())
        val reactive = PromptRules.reactive(prompts, kind, digest.facts).take(MAX_REACTIVE)
        // What past reviews of this kind already asked, oldest first, so the rotation moves on.
        val shown = reviews.all().filter { it.kind == kind }.sortedBy { it.periodStart }.flatMap { it.reflections }.map { it.promptId }
        val rotation = PromptRules.rotation(prompts, kind, shown, QUESTIONS - reactive.size)
        return reactive + rotation.map { prompt -> ReviewQuestion(prompt.id, PromptRules.text(prompt, kind)) }
    }

    /** Writes an answer; it is saved when the step is left, and at once when the review is finished. */
    fun answer(promptId: String, text: String) = answers.update { it + (promptId to text) }

    fun setMood(mood: Int) = write { review -> reviews.setMood(review.id, mood.takeIf { it != uiState.value.mood }) }

    fun setEnergy(energy: Int) = write { review -> reviews.setEnergy(review.id, energy.takeIf { it != uiState.value.energy }) }

    /** What to do with a task still open from the period: to the next period, done, or dropped. */
    fun decide(task: TaskItem, decision: PlanDecision) {
        viewModelScope.launch(io) {
            when (decision) {
                PlanDecision.TOMORROW -> tasks.plan(task.id, maxOf(today(), ReviewLookBack.periodEnd(kind, periodStart).plusDays(1)))
                PlanDecision.DONE -> tasks.setDone(task.id, true)
                PlanDecision.DROPPED -> tasks.drop(task.id)
                else -> Unit
            }
        }
    }

    /** Copies the goals of this period into the next one, when it has none yet (docs/goals.md). */
    fun copyGoals() {
        viewModelScope.launch(io) {
            goals.copyPrevious(ReviewLookBack.horizonOf(kind), ReviewLookBack.periodEnd(kind, periodStart).plusDays(1))
        }
    }

    /** Adds a goal to the period ahead. False when the draft isn't one the goal list keeps. */
    suspend fun addGoal(draft: GoalDraft): Boolean = withContext(io) { goals.add(draft) != null }

    /** The goal the Add button starts from: the next period, of this review's horizon. */
    fun nextGoalDraft(): GoalDraft = GoalDraft(
        title = "",
        horizon = ReviewLookBack.horizonOf(kind),
        periodStart = ReviewLookBack.periodEnd(kind, periodStart).plusDays(1),
        mode = GoalRules.MODE_DONE,
    )

    fun next() {
        saveAnswers()
        step.value = when (step.value) {
            ReviewStep.LOOK_BACK -> ReviewStep.TASKS
            ReviewStep.TASKS -> ReviewStep.REFLECT
            ReviewStep.REFLECT -> ReviewStep.RATE
            ReviewStep.RATE -> ReviewStep.GOALS
            ReviewStep.GOALS, ReviewStep.DONE -> {
                viewModelScope.launch(io) { rituals.record(ritual(), today()) }
                ReviewStep.DONE
            }
        }
    }

    /** Back a step; false where back should leave the review. */
    fun back(): Boolean {
        val before = step.value
        if (before == ReviewStep.LOOK_BACK) return false
        saveAnswers()
        step.value = when (before) {
            ReviewStep.TASKS -> ReviewStep.LOOK_BACK
            ReviewStep.REFLECT -> ReviewStep.TASKS
            ReviewStep.RATE -> ReviewStep.REFLECT
            ReviewStep.GOALS -> ReviewStep.RATE
            else -> ReviewStep.GOALS
        }
        return true
    }

    /** Keeps what was written; the screen calls it when it leaves, so nothing is lost. */
    fun saveAnswers() {
        val review = uiState.value.review ?: opened.value ?: return
        val asked = questions.value
        if (asked.isEmpty()) return
        val written = answers.value
        viewModelScope.launch(io) {
            reviews.setReflections(review.id, asked.map { Reflection(it.promptId, written[it.promptId].orEmpty()) })
        }
    }

    private fun ritual(): String = if (kind == "monthly") RitualRunList.MONTHLY_REVIEW else RitualRunList.WEEKLY_REVIEW

    private fun write(work: (ReviewItem) -> Unit) {
        val review = uiState.value.review ?: opened.value ?: return
        viewModelScope.launch(io) { work(review) }
    }

    private data class Screen(
        val step: ReviewStep,
        val questions: List<ReviewQuestion>,
        val answers: Map<String, String>,
        val review: ReviewItem?,
    )

    private companion object {
        /** How many prompts a review asks, and how many of them may come from the period's facts. */
        const val QUESTIONS = 3
        const val MAX_REACTIVE = 2
    }
}
