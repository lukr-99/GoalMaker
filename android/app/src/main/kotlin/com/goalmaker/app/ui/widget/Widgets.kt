package com.goalmaker.app.ui.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import kotlinx.coroutines.CancellationException

/**
 * Draws widgets again: after a tap on one, and after a sync brings changes in.
 *
 * Glance's own `updateAll` finds a widget's placed copies by the drawing class's name. In a release
 * build R8 folded TodayWidget, HabitsWidget and QuickAddWidget into one class (and Goals with
 * Motivation), so `updateAll` drew each kind into every widget of the group and the last one won.
 * The launcher's ids per receiver are what is placed, so those are what this draws.
 */
object Widgets {
    suspend fun refresh(context: Context) {
        WidgetKind.entries.forEach { redraw(context, it) }
    }

    /** Draws every placed widget of one [kind]; one that fails is written down and the rest still draw. */
    suspend fun redraw(context: Context, kind: WidgetKind) {
        ids(context, kind).forEach { id -> redraw(context, kind, id) }
    }

    /** Draws one placed widget, by the id the launcher gave it. */
    suspend fun redraw(context: Context, kind: WidgetKind, appWidgetId: Int) {
        try {
            kind.create().update(context, GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId))
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            WidgetFallback.log(context, error)
        }
    }

    /** The ids the launcher gave to the placed widgets of [kind]. */
    fun ids(context: Context, kind: WidgetKind): List<Int> =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, kind.receiver)).toList()
}
