package com.goalmaker.app.ui.widget

import android.content.Context
import com.goalmaker.app.GoalMakerApplication
import com.goalmaker.app.domain.planning.PlanningDay
import java.time.LocalDate
import java.time.LocalDateTime

/** What the widgets read: the replica through the app's own lists, on the caller's thread. */
object WidgetData {
    fun today(context: Context): List<WidgetTask> {
        val graph = (context.applicationContext as GoalMakerApplication).graph
        return WidgetContent.today(graph.tasks.all(), day(context))
    }

    fun doneToday(context: Context): Pair<Int, Int> {
        val graph = (context.applicationContext as GoalMakerApplication).graph
        return WidgetContent.done(graph.tasks.all(), day(context))
    }

    fun habits(context: Context): List<WidgetHabit> {
        val graph = (context.applicationContext as GoalMakerApplication).graph
        return WidgetContent.habits(graph.habits.read(), day(context))
    }

    /** The owner's planning day, which starts at their day-start hour, not midnight. */
    fun day(context: Context): LocalDate {
        val graph = (context.applicationContext as GoalMakerApplication).graph
        return PlanningDay.of(LocalDateTime.now(), graph.settings.dayStartHour.value)
    }
}
