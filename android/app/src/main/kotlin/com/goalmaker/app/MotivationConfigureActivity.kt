package com.goalmaker.app

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.goalmaker.app.ui.theme.GoalMakerTheme
import com.goalmaker.app.ui.widget.MotivationConfigureScreen
import com.goalmaker.app.ui.widget.MotivationStore
import com.goalmaker.app.ui.widget.MotivationWidget
import kotlinx.coroutines.launch

/**
 * The Motivation widget's configure screen (docs/widgets.md): the launcher opens it when the widget
 * is placed, and the widget opens it again on a tap while it shows the owner's own words. What is
 * picked is kept for that one widget, on this device only.
 */
class MotivationConfigureActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Backing out of the first setup leaves the widget off the home screen, as Android expects.
        setResult(RESULT_CANCELED, result(widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val graph = (application as GoalMakerApplication).graph
        val store = MotivationStore.of(this)
        val initial = store.read(widgetId)
        setContent {
            val appearance by graph.settings.appearance.collectAsStateWithLifecycle()
            GoalMakerTheme(graph.design, appearance, graph.logo) {
                MotivationConfigureScreen(
                    initial = initial,
                    onSave = { choice ->
                        store.write(widgetId, choice)
                        lifecycleScope.launch {
                            MotivationWidget.update(applicationContext, widgetId)
                            setResult(RESULT_OK, result(widgetId))
                            finish()
                        }
                    },
                    onCancel = ::finish,
                )
            }
        }
    }

    private fun result(widgetId: Int) = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)

    companion object {
        /** What a Motivation widget showing the owner's words starts on a tap. */
        fun intent(context: Context, widgetId: Int): Intent =
            Intent(context, MotivationConfigureActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                // One per widget, so two widgets never share a pending intent.
                .setData(Uri.parse("goalmaker-widget://motivation/$widgetId"))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
    }
}
