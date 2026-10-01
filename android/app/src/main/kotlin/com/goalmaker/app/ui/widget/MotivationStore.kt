package com.goalmaker.app.ui.widget

import android.content.Context
import android.content.SharedPreferences
import com.goalmaker.app.application.planning.GoalHorizon

/**
 * Where each Motivation widget's setup is kept: on this device only, one entry per widget the
 * launcher placed, keyed by its app widget id. It never syncs, because a widget belongs to the
 * home screen it sits on. A widget with no entry yet shows this week's goals.
 */
class MotivationStore(private val prefs: SharedPreferences) {
    fun read(widgetId: Int): MotivationChoice {
        val fallback = MotivationChoice()
        return MotivationChoice(
            mode = MotivationMode.of(prefs.getString(key(widgetId, MODE), null)) ?: fallback.mode,
            text = prefs.getString(key(widgetId, TEXT), null) ?: fallback.text,
            horizon = GoalHorizon.of(prefs.getString(key(widgetId, HORIZON), null))
                ?.takeIf { it in MotivationChoice.HORIZONS } ?: fallback.horizon,
        )
    }

    fun write(widgetId: Int, choice: MotivationChoice) {
        prefs.edit()
            .putString(key(widgetId, MODE), choice.mode.id)
            .putString(key(widgetId, TEXT), choice.text.trim().take(MotivationChoice.MAX_TEXT))
            .putString(key(widgetId, HORIZON), choice.horizon.id)
            .apply()
    }

    /** The widget was taken off the home screen, so its setup goes too. */
    fun forget(widgetId: Int) {
        prefs.edit().remove(key(widgetId, MODE)).remove(key(widgetId, TEXT)).remove(key(widgetId, HORIZON)).apply()
    }

    private fun key(widgetId: Int, field: String) = "$widgetId.$field"

    companion object {
        private const val FILE = "motivation_widgets"
        private const val MODE = "mode"
        private const val TEXT = "text"
        private const val HORIZON = "horizon"

        fun of(context: Context): MotivationStore =
            MotivationStore(context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE))
    }
}
