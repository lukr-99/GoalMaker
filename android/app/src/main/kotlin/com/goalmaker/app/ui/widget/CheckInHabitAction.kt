package com.goalmaker.app.ui.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.goalmaker.app.GoalMakerApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A tap on the Habits widget: one check-in for today, the same tap the app's button takes. An amount
 * fills to its target (docs/habits.md, "One tap").
 */
class CheckInHabitAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[HABIT_ID] ?: return
        val graph = (context.applicationContext as GoalMakerApplication).graph
        withContext(Dispatchers.IO) {
            val day = WidgetData.day(context)
            if (!graph.habits.tap(id, day)) graph.habits.fill(id, day)
        }
        Widgets.refresh(context)
    }

    companion object {
        val HABIT_ID = ActionParameters.Key<String>("habitId")
    }
}
