package com.goalmaker.app.ui.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.goalmaker.app.GoalMakerApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A tap on the Habits widget: one check-in for today, the same tap the app's ring takes. */
class CheckInHabitAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[HABIT_ID] ?: return
        val graph = (context.applicationContext as GoalMakerApplication).graph
        withContext(Dispatchers.IO) { graph.habits.tap(id, WidgetData.day(context)) }
        Widgets.refresh(context)
    }

    companion object {
        val HABIT_ID = ActionParameters.Key<String>("habitId")
    }
}
