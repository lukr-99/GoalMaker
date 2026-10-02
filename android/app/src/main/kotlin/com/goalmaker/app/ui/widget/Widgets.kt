package com.goalmaker.app.ui.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import kotlinx.coroutines.CancellationException

/**
 * Draws widgets again: after a tap on one, and after the replica changes (a change in the app, or
 * what a sync brings in).
 *
 * Glance's own `updateAll` finds a widget's placed copies by the drawing class's name. In a release
 * build R8 folded TodayWidget, HabitsWidget and QuickAddWidget into one class (and Goals with
 * Motivation), so `updateAll` drew each kind into every widget of the group and the last one won.
 * The launcher's ids per receiver are what is placed, so those are what this draws.
 */
object Widgets {
    /**
     * How many times [redraw] drew this widget, kept in the widget's Glance state. Glance keeps a
     * widget's session running for a while after it draws, and an update in that time only composes
     * again: `provideGlance` does not run, so what it read stays. A new mark changes the state,
     * which [fresh] reads again on.
     */
    val DRAWN = longPreferencesKey("drawn")

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
            val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(context, glanceId) { state -> state[DRAWN] = (state[DRAWN] ?: 0L) + 1 }
            kind.create().update(context, glanceId)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            WidgetFallback.log(context, error)
        }
    }

    /** The ids the launcher gave to the placed widgets of [kind]. */
    fun ids(context: Context, kind: WidgetKind): List<Int> =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, kind.receiver)).toList()

    /**
     * What a widget shows: [first], which `provideGlance` read before the session began, and then
     * [read] again each time [redraw] draws the widget while that session still runs.
     */
    @Composable
    fun <T> fresh(first: T, read: suspend () -> T): T {
        val drawn = currentState(DRAWN) ?: 0L
        val start = remember { drawn }
        val shown by produceState(first, drawn) { if (drawn != start) value = read() }
        return shown
    }
}
