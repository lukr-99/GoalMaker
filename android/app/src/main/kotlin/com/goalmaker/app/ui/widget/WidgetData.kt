package com.goalmaker.app.ui.widget

import android.content.Context
import com.goalmaker.app.GoalMakerApplication
import com.goalmaker.app.application.planning.GoalHorizon
import com.goalmaker.app.domain.planning.PlanningDay
import java.time.LocalDate
import java.time.LocalDateTime

/** What the widgets read: the replica through the app's own lists, on the caller's thread. */
object WidgetData {
    /** The replica tables the widgets read; a change to any of them draws the widgets again. */
    val TABLES = listOf("tasks", "habits", "habit_checkins", "habit_pauses", "goals", "goal_entries", "projects")

    fun today(context: Context): List<WidgetTask> {
        val graph = (context.applicationContext as GoalMakerApplication).graph
        return WidgetContent.today(graph.tasks.all(), day(context), projects = graph.projects.all())
    }

    fun doneToday(context: Context): Pair<Int, Int> {
        val graph = (context.applicationContext as GoalMakerApplication).graph
        return WidgetContent.done(graph.tasks.all(), day(context))
    }

    fun habits(context: Context): List<WidgetHabit> {
        val graph = (context.applicationContext as GoalMakerApplication).graph
        return WidgetContent.habits(graph.habits.read(), day(context))
    }

    fun goalLines(context: Context, horizon: GoalHorizon): List<String> {
        val graph = (context.applicationContext as GoalMakerApplication).graph
        return WidgetContent.goalLines(graph.goals.all(), horizon, day(context))
    }

    fun goalsAllDone(context: Context, horizon: GoalHorizon): Boolean {
        val graph = (context.applicationContext as GoalMakerApplication).graph
        return WidgetContent.goalsAllDone(graph.goals.all(), horizon, day(context))
    }

    fun rings(context: Context): List<WidgetRing> {
        val graph = (context.applicationContext as GoalMakerApplication).graph
        return WidgetContent.rings(graph.goals.all(), graph.goals.entries(), graph.tasks.all(), graph.habits.read(), day(context))
    }

    /** The owner's planning day, which starts at their day-start hour, not midnight. */
    fun day(context: Context): LocalDate {
        val graph = (context.applicationContext as GoalMakerApplication).graph
        return PlanningDay.of(LocalDateTime.now(), graph.settings.dayStartHour.value)
    }
}
