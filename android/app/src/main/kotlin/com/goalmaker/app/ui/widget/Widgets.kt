package com.goalmaker.app.ui.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

/** Draws every widget again: after a tap on one, and after a sync brings changes in. */
object Widgets {
    suspend fun refresh(context: Context) {
        TodayWidget().updateAll(context)
        HabitsWidget().updateAll(context)
    }
}
