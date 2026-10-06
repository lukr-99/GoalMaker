package com.goalmaker.app.ui.widget

import com.goalmaker.app.application.planning.GoalEntryItem
import com.goalmaker.app.application.planning.GoalHorizon
import com.goalmaker.app.application.planning.GoalItem
import com.goalmaker.app.application.planning.GoalRules
import com.goalmaker.app.application.planning.HabitCheckin
import com.goalmaker.app.application.planning.HabitData
import com.goalmaker.app.application.planning.HabitRules
import com.goalmaker.app.application.planning.HabitStanding
import com.goalmaker.app.application.planning.LifeGoalItem
import com.goalmaker.app.application.planning.LifeGoalPicture
import com.goalmaker.app.application.planning.LifeGoalRules
import com.goalmaker.app.application.planning.ListRules
import com.goalmaker.app.application.planning.ProjectItem
import com.goalmaker.app.application.planning.TaskItem
import com.goalmaker.app.ui.goals.GoalBoard
import java.time.Instant
import java.time.LocalDate

/**
 * What the home screen widgets show (docs/widgets.md, spec stories 85 to 87), worked out from the
 * same rules the screens use, so a widget never disagrees with the app.
 */
object WidgetContent {
    /** At most this many rows fit a widget before it asks the owner to open the app. */
    const val ROWS = 8

    /**
     * The Habits widget scrolls, so it takes every habit on Today up to this many: a cap only so a
     * launcher is never handed an endless list.
     */
    const val HABIT_ROWS = 60

    /** Below this width (dp) two compact habit tiles side by side would cut their names too short. */
    const val HABIT_GRID_MIN_WIDTH = 220f

    /** The room the Habits widget's padding and header take (dp), and the height of one habit row. */
    const val HABIT_CHROME = 52f
    const val HABIT_ROW = 28f

    /**
     * What is still open today, in the order Today shows it: top priorities, then by time, then the
     * rest, a project item with its project's name from [projects] (the ones that are not deleted).
     */
    fun today(tasks: List<TaskItem>, today: LocalDate, rows: Int = ROWS, projects: List<ProjectItem> = emptyList()): List<WidgetTask> {
        val sections = ListRules.lists(tasks, today).todaySections
        val names = projects.filterNot(ProjectItem::deleted).associate { it.id to it.name }
        return (sections.priorities + sections.scheduled + sections.more).take(rows).map { task ->
            WidgetTask(
                id = task.id,
                title = task.title,
                time = task.plannedTime?.toString().orEmpty(),
                topPriority = task.topPriority,
                project = task.projectId?.let(names::get).orEmpty(),
            )
        }
    }

    /** How much of today is behind: what the widget's header says. */
    fun done(tasks: List<TaskItem>, today: LocalDate): Pair<Int, Int> {
        val summary = ListRules.lists(tasks, today).summary
        return summary.done to summary.total
    }

    /** Today's habits, the ones on Today's ring row, with how far each has got. */
    fun habits(data: HabitData, today: LocalDate, rows: Int = HABIT_ROWS): List<WidgetHabit> =
        data.habits.filter { !it.deleted && HabitRules.onToday(it, today, data.pausesOf(it.id)) }
            .take(rows)
            .map { habit ->
                val checkins = data.checkinsOf(habit.id)
                val ring = HabitRules.ring(habit, today, checkins) ?: 0.0
                val target = habit.target ?: 1.0
                // Done and left as Today reads them (contracts/vectors/habits.json, standings): a limit is neither.
                val standing = HabitRules.standing(habit, today, checkins, data.pausesOf(habit.id))
                WidgetHabit(
                    id = habit.id,
                    name = habit.name,
                    emoji = habit.emoji.orEmpty(),
                    ring = ring,
                    done = standing == HabitStanding.DONE,
                    left = standing == HabitStanding.LEFT,
                    count = if (habit.measure == HabitRules.CHECK) "" else amount(ring * target, target, habit.unit),
                    // A check or a count moves with one tap, and an amount fills to its target (docs/habits.md,
                    // "One tap"); a limit's amount, or one already at its target, asks for its value in the app.
                    tappable = habit.measure != HabitRules.AMOUNT || HabitRules.fill(habit, todays(checkins, today)) != null,
                )
            }

    /**
     * How many columns the Habits widget lays [count] habits out in, at [width] by [height] dp: two
     * compact tiles side by side once the widget is wide enough for them and the habits would not all
     * fit one under another; otherwise one. Either way the list scrolls when there are more than fit.
     */
    fun habitColumns(count: Int, width: Float, height: Float): Int {
        if (width < HABIT_GRID_MIN_WIDTH) return 1
        val fit = maxOf(1, ((height - HABIT_CHROME) / HABIT_ROW).toInt())
        return if (count > fit) 2 else 1
    }

    // Today's value of a habit, leaving out a skipped or failed day.
    private fun todays(checkins: List<HabitCheckin>, today: LocalDate): Double =
        checkins.firstOrNull { !it.deleted && it.day == today && !it.skipped && !it.failed }?.value ?: 0.0

    /** How many of today's habits are still open, for the header. */
    fun habitsLeft(habits: List<WidgetHabit>): Int = habits.count(WidgetHabit::left)

    /**
     * The Motivation widget's goals: the open goals of this [horizon]'s current period as plain
     * written lines, in the order the owner keeps them, each with its emoji. A goal marked done or
     * dropped leaves the widget, so it shows what is still to reach.
     */
    fun goalLines(all: List<GoalItem>, horizon: GoalHorizon, today: LocalDate): List<String> =
        current(all, horizon, today).filter { it.status == GoalRules.OPEN }
            .sortedWith(compareBy(GoalItem::position).thenBy { it.title.lowercase(java.util.Locale.ROOT) })
            .map { goal -> goal.emoji?.takeIf(String::isNotBlank)?.let { "$it ${goal.title}" } ?: goal.title }

    /** True when this [horizon]'s current period has goals and every one not dropped is done. */
    fun goalsAllDone(all: List<GoalItem>, horizon: GoalHorizon, today: LocalDate): Boolean {
        val kept = current(all, horizon, today).filter { it.status != GoalRules.DROPPED }
        return kept.isNotEmpty() && kept.all { it.status == GoalRules.DONE }
    }

    private fun current(all: List<GoalItem>, horizon: GoalHorizon, today: LocalDate): List<GoalItem> {
        val start = GoalRules.periodStart(horizon, today)
        return all.filter { !it.deleted && it.horizon == horizon && it.periodStart == start }
    }

    /** The Goals widget's rings: this year, month, week and today, by the Goals screen's own board. */
    fun rings(
        all: List<GoalItem>,
        entries: List<GoalEntryItem>,
        tasks: List<TaskItem>,
        habits: HabitData,
        today: LocalDate,
    ): List<WidgetRing> = GoalBoard.build(all, entries, tasks, today, habits).rings.map { section ->
        WidgetRing(section.horizon, section.fraction, section.hits, section.rows.size)
    }

    private fun amount(value: Double, target: Double, unit: String?): String {
        val text = "${number(value)} of ${number(target)}"
        return if (unit.isNullOrBlank()) text else "$text $unit"
    }

    /** How long the Life goals widget shows one slide before the next. */
    const val LIFE_GOAL_SLIDE_MINUTES = 30L

    /**
     * Every slide the Life goals widget goes through: each picture of each open life goal, in the
     * owner's order and the pictures' order, and one slide for a life goal without pictures.
     */
    fun lifeGoalSlides(goals: List<LifeGoalItem>, pictures: List<LifeGoalPicture>, today: LocalDate): List<WidgetLifeGoal> {
        val byGoal = pictures.filterNot(LifeGoalPicture::deleted).groupBy(LifeGoalPicture::lifeGoalId)
        return LifeGoalRules.open(goals).flatMap { goal ->
            val slide = WidgetLifeGoal(goal.id, goal.title, goal.why, LifeGoalRules.timeLeft(goal.by, today))
            byGoal[goal.id].orEmpty().sortedWith(compareBy<LifeGoalPicture> { it.position }.thenBy { it.id })
                .map { slide.copy(pictureId = it.id) }
                .ifEmpty { listOf(slide) }
        }
    }

    /** The slide on show at [now]: the next one every [LIFE_GOAL_SLIDE_MINUTES], round and round. */
    fun lifeGoalSlide(slides: List<WidgetLifeGoal>, now: Instant): WidgetLifeGoal? {
        if (slides.isEmpty()) return null
        val slot = now.epochSecond / (LIFE_GOAL_SLIDE_MINUTES * 60)
        return slides[Math.floorMod(slot, slides.size.toLong()).toInt()]
    }

    private fun number(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else String.format(java.util.Locale.ROOT, "%.1f", value)
}
