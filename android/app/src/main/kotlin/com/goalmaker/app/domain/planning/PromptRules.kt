package com.goalmaker.app.domain.planning

import kotlin.math.ceil

/**
 * Which prompts a review asks (docs/reviews.md, contracts/vectors/reviews.json): the rotation through
 * the library's categories, the prompts the period's facts call for, and the text of a prompt once the
 * period and the subject are filled in.
 */
object PromptRules {
    /** A goal this far from where it should be is worth asking about. */
    private const val GOAL_GAP = 0.2

    /** A task moved this many times is worth asking about. */
    private const val SLIPPING_MOVES = 3

    /** A streak this long is worth asking about. */
    private const val LONG_STREAK = 7

    /** The prompts of the library that suit [kind] and wait for no trigger, in the file's order. */
    fun library(library: PromptLibrary, kind: String): List<ReviewPrompt> =
        library.prompts.filter { it.trigger == null && kind in it.reviews }

    /**
     * The prompt of [category] to ask next: the one shown longest ago, which is any it hasn't shown yet,
     * in the file's order. Null when the category has nothing for this kind of review.
     */
    fun next(library: PromptLibrary, kind: String, category: String, shown: List<String>): ReviewPrompt? =
        library(library, kind).filter { it.category == category }.minByOrNull { prompt ->
            shown.lastIndexOf(prompt.id).let { if (it < 0) -1 else it }
        }

    /**
     * The [count] prompts a review asks, walking the categories from the one after the last id in [shown]
     * and taking one from each (a category with nothing left to say is passed over).
     */
    fun rotation(library: PromptLibrary, kind: String, shown: List<String>, count: Int): List<ReviewPrompt> {
        val categories = library.categories.filter { category -> library(library, kind).any { it.category == category } }
        if (categories.isEmpty() || count <= 0) return emptyList()
        val last = shown.lastOrNull()?.let { id -> library[id]?.category }
        val from = categories.indexOf(last).let { if (it < 0) 0 else it + 1 }
        val chosen = mutableListOf<ReviewPrompt>()
        val seen = shown.toMutableList()
        for (step in categories.indices) {
            if (chosen.size == count) break
            val category = categories[(from + step) % categories.size]
            val prompt = next(library, kind, category, seen) ?: continue
            chosen += prompt
            seen += prompt.id
        }
        return chosen
    }

    /**
     * The triggered prompts [facts] call for, worst first within each trigger: a goal behind plan, a habit
     * mostly missed, a task that keeps moving, a long streak, a goal ahead of plan, a period without goals,
     * and a quiet or busy period (not in a yearly review).
     */
    fun reactive(library: PromptLibrary, kind: String, facts: PeriodFacts): List<ReviewQuestion> {
        val triggers = library.prompts.filter { it.trigger != null && kind in it.reviews }.associateBy { it.trigger }
        val questions = mutableListOf<ReviewQuestion>()

        fun ask(trigger: String, subject: String? = null) {
            triggers[trigger]?.let { prompt -> questions += ReviewQuestion(prompt.id, text(prompt, kind, subject), subject) }
        }

        facts.goals
            .filter { it.expected - it.fraction >= GOAL_GAP }
            .sortedWith(compareByDescending<PeriodFacts.GoalFact> { it.expected - it.fraction }.thenBy(PeriodFacts.GoalFact::title))
            .firstOrNull()
            ?.let { goal -> ask("goal_behind", goal.title) }
        facts.habits
            .filter { it.periods >= 2 && it.missed >= ceil(it.periods / 2.0).toInt() }
            .sortedWith(compareByDescending<PeriodFacts.HabitFact> { it.missed }.thenBy(PeriodFacts.HabitFact::name))
            .firstOrNull()
            ?.let { habit -> ask("habit_missed", habit.name) }
        facts.tasks
            .filter { it.moves >= SLIPPING_MOVES }
            .sortedWith(compareByDescending<PeriodFacts.TaskFact> { it.moves }.thenBy(PeriodFacts.TaskFact::title))
            .firstOrNull()
            ?.let { task -> ask("task_slipping", task.title) }
        facts.habits
            .filter { it.streak >= LONG_STREAK }
            .sortedWith(compareByDescending<PeriodFacts.HabitFact> { it.streak }.thenBy(PeriodFacts.HabitFact::name))
            .firstOrNull()
            ?.let { habit -> ask("habit_streak", habit.name) }
        facts.goals
            .filter { it.fraction - it.expected >= GOAL_GAP }
            .sortedWith(compareByDescending<PeriodFacts.GoalFact> { it.fraction - it.expected }.thenBy(PeriodFacts.GoalFact::title))
            .firstOrNull()
            ?.let { goal -> ask("goal_ahead", goal.title) }
        if (facts.goals.isEmpty()) ask("no_goals")
        if (facts.averageDone > 0.0) {
            if (facts.doneTasks <= facts.averageDone / 2.0) ask("quiet_period")
            if (facts.doneTasks >= facts.averageDone * 1.5) ask("busy_period")
        }
        return questions
    }

    /** A prompt's text for a [kind] review, with the period named and [subject] filled in. */
    fun text(prompt: ReviewPrompt, kind: String, subject: String? = null): String =
        prompt.text.replace("{period}", period(kind)).replace("{subject}", subject.orEmpty())

    /** What a review of [kind] calls its period: week, month or year (the kinds of ReviewRules). */
    fun period(kind: String): String = when (kind) {
        "monthly" -> "month"
        "yearly" -> "year"
        else -> "week"
    }
}
