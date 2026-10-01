package com.goalmaker.app.ui.widget

import android.content.Context
import android.content.Intent
import androidx.glance.action.Action
import androidx.glance.appwidget.action.actionStartActivity
import com.goalmaker.app.MainActivity

/** Tapping a widget's background opens the app itself, or one place in it. */
object WidgetOpen {
    /** Set on the intent when the app should open on the Goals place. */
    const val EXTRA_OPEN_GOALS = "com.goalmaker.app.OPEN_GOALS"

    fun app(context: Context): Action = actionStartActivity(main(context))

    /** The app, on the Goals place (the Goals and Motivation widgets). */
    fun goals(context: Context): Action = actionStartActivity(main(context).putExtra(EXTRA_OPEN_GOALS, true))

    private fun main(context: Context): Intent = Intent(context, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
