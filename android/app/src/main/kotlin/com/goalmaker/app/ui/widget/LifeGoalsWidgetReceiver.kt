package com.goalmaker.app.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * What the launcher talks to for the Life goals widget. The launcher's update every 30 minutes
 * (life_goals_widget_info.xml) becomes a full redraw, so a running session reads the next slide
 * instead of drawing the one it already has (see [Widgets.fresh]).
 */
class LifeGoalsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LifeGoalsWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                appWidgetIds.forEach { Widgets.redraw(context, WidgetKind.LIFE_GOALS, it) }
            } finally {
                pending.finish()
            }
        }
    }
}
