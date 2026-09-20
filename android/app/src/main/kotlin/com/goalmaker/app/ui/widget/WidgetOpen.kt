package com.goalmaker.app.ui.widget

import android.content.Context
import android.content.Intent
import androidx.glance.action.Action
import androidx.glance.appwidget.action.actionStartActivity
import com.goalmaker.app.MainActivity

/** Tapping a widget's background opens the app itself. */
object WidgetOpen {
    fun app(context: Context): Action = actionStartActivity(
        Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        },
    )
}
