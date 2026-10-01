package com.goalmaker.app.ui.widget

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.goalmaker.app.R
import com.goalmaker.app.data.diagnostics.CrashLog
import kotlinx.coroutines.CancellationException

/**
 * What a widget shows when its rows could not be read, instead of Android's "Can't load widget"
 * box: the app's name and a line to open it. The error goes to the crash log, so it can be read
 * later (`android/tools/pull-debug-files.ps1 -FilePattern '\.log$'`).
 */
object WidgetFallback {
    private const val TAG = "GoalMakerWidget"

    /** Writes a widget's failure down; it never throws itself. */
    fun log(context: Context, error: Throwable) {
        runCatching { Log.w(TAG, "A widget could not be drawn", error) }
        runCatching { CrashLog.write(context.filesDir, error) }
    }

    /** Runs [load], or writes its failure down and gives null so the widget can show [Content]. */
    suspend fun <T> load(context: Context, load: suspend () -> T): T? = try {
        load()
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (error: Exception) {
        log(context, error)
        null
    }

    /** A plain card in the default theme's colors, since the owner's theme may be what failed. */
    @Composable
    fun Content(context: Context) {
        Column(
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color(context.getColor(R.color.widget_preview_background)))
                .cornerRadius(20.dp)
                .padding(12.dp)
                .clickable(WidgetOpen.app(context)),
        ) {
            Text(
                context.getString(R.string.app_name),
                style = TextStyle(color = color(context, R.color.widget_preview_accent), fontWeight = FontWeight.Bold),
            )
            Text(
                context.getString(R.string.widget_unavailable),
                style = TextStyle(color = color(context, R.color.widget_preview_text_muted)),
            )
        }
    }

    // The preview colors follow the system's light or dark mode, like the picker's previews.
    private fun color(context: Context, id: Int) = ColorProvider(Color(context.getColor(id)))
}
