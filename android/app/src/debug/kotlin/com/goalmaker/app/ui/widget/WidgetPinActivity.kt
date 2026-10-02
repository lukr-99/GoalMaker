package com.goalmaker.app.ui.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.util.Log

/**
 * Dev builds only: asks the launcher to place one widget, so a script can put every widget on an
 * emulator's home screen without dragging it out of the picker (android/tools/check-widgets.ps1):
 *
 *     adb shell am start -n com.goalmaker.app.debug/com.goalmaker.app.ui.widget.WidgetPinActivity --es kind TODAY
 *
 * The launcher then shows its own "Add to home screen" sheet, which the script confirms.
 */
class WidgetPinActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val name = intent.getStringExtra(EXTRA_KIND).orEmpty()
        val kind = WidgetKind.entries.firstOrNull { it.name == name }
        val manager = AppWidgetManager.getInstance(this)
        when {
            kind == null -> Log.w(TAG, "No widget kind '$name'; use one of ${WidgetKind.entries.joinToString()}")
            !manager.isRequestPinAppWidgetSupported -> Log.w(TAG, "The launcher cannot place widgets for apps")
            else -> manager.requestPinAppWidget(ComponentName(this, kind.receiver), null, null)
        }
        finish()
    }

    private companion object {
        const val EXTRA_KIND = "kind"
        const val TAG = "GoalMakerWidget"
    }
}
