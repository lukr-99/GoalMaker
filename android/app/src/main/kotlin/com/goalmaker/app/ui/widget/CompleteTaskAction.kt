package com.goalmaker.app.ui.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.goalmaker.app.GoalMakerApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A tick on the Today widget: the task is finished and every widget is drawn again. */
class CompleteTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[TASK_ID] ?: return
        val graph = (context.applicationContext as GoalMakerApplication).graph
        withContext(Dispatchers.IO) { graph.tasks.setDone(id, true) }
        Widgets.refresh(context)
    }

    companion object {
        val TASK_ID = ActionParameters.Key<String>("taskId")
    }
}
