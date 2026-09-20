package com.goalmaker.app.ui.widget

import com.goalmaker.app.application.planning.HabitData
import com.goalmaker.app.application.planning.HabitRules
import com.goalmaker.app.application.planning.ListRules
import com.goalmaker.app.application.planning.TaskItem
import java.time.LocalDate

/**
 * What the home screen widgets show (docs/widgets.md, spec stories 85 and 86), worked out from the
 * same rules the screens use, so a widget never disagrees with the app.
 */
object WidgetContent {
    /** At most this many rows fit a widget before it asks the owner to open the app. */
    const val ROWS = 8

    /** What is still open today, in the order Today shows it: top priorities, then by time, then the rest. */
    fun today(tasks: List<TaskItem>, today: LocalDate, rows: Int = ROWS): List<WidgetTask> {
        val sections = ListRules.lists(tasks, today).todaySections
        return (sections.priorities + sections.scheduled + sections.more).take(rows).map { task ->
            WidgetTask(
                id = task.id,
                title = task.title,
                time = task.plannedTime?.toString().orEmpty(),
                topPriority = task.topPriority,
            )
        }
    }

    /** How much of today is behind: what the widget's header says. */
    fun done(tasks: List<TaskItem>, today: LocalDate): Pair<Int, Int> {
        val summary = ListRules.lists(tasks, today).summary
        return summary.done to summary.total
    }

    /** Today's habits, the ones due first, with how far each has got. */
    fun habits(data: HabitData, today: LocalDate, rows: Int = ROWS): List<WidgetHabit> =
        data.habits.filter { !it.deleted && !it.archived && HabitRules.isDue(it, today) }
            .take(rows)
            .map { habit ->
                val checkins = data.checkinsOf(habit.id)
                val ring = HabitRules.ring(habit, today, checkins) ?: 0.0
                val target = habit.target ?: 1.0
                WidgetHabit(
                    id = habit.id,
                    name = habit.name,
                    emoji = habit.emoji.orEmpty(),
                    ring = ring,
                    done = ring >= 1.0,
                    count = if (habit.measure == HabitRules.CHECK) "" else amount(ring * target, target, habit.unit),
                    // A check or a count moves with one tap; an amount asks for its value in the app.
                    tappable = habit.measure != HabitRules.AMOUNT,
                )
            }

    /** How many of today's habits are still open, for the header. */
    fun habitsLeft(habits: List<WidgetHabit>): Int = habits.count { !it.done }

    private fun amount(value: Double, target: Double, unit: String?): String {
        val text = "${number(value)} of ${number(target)}"
        return if (unit.isNullOrBlank()) text else "$text $unit"
    }

    private fun number(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else String.format(java.util.Locale.ROOT, "%.1f", value)
}
