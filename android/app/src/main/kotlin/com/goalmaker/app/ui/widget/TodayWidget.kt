package com.goalmaker.app.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.goalmaker.app.R

/**
 * The Today widget (spec, story 85): today's tasks with a tick that finishes one without opening the
 * app, and a line for how much is behind. It reads the replica, so it shows what the app shows, and a
 * tick goes through the outbox like any other change.
 */
class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val read = suspend {
            WidgetFallback.load(context) {
                Triple(WidgetSkin.of(context), WidgetData.today(context), WidgetData.doneToday(context))
            }
        }
        val first = read()
        provideContent {
            val shown = Widgets.fresh(first, read)
            if (shown == null) {
                WidgetFallback.Content(context)
            } else {
                val (skin, tasks, counts) = shown
                Content(context, skin, tasks, counts.first, counts.second)
            }
        }
    }

    /** What the widget draws, apart from reading it, so a test can lay it out. */
    @Composable
    internal fun Content(context: Context, skin: WidgetSkin, tasks: List<WidgetTask>, done: Int, total: Int) {
        Column(
            GlanceModifier
                .fillMaxSize()
                .background(Color(skin.background))
                .cornerRadius(20.dp)
                .padding(12.dp)
                .clickable(WidgetOpen.app(context)),
        ) {
            // A day with nothing planned has nothing to count, so the header says only "Today".
            Text(
                if (total == 0) {
                    context.getString(R.string.widget_today_title)
                } else {
                    context.getString(R.string.widget_today_header, done, total)
                },
                style = TextStyle(color = ColorProvider(Color(skin.accent)), fontWeight = FontWeight.Bold),
            )
            // A Spacer needs a size: with only padding, Glance stretches it over the rest of the
            // widget and the rows below go out of sight (docs/pitfalls.md).
            Spacer(GlanceModifier.height(6.dp))
            if (tasks.isEmpty()) {
                Text(
                    context.getString(if (total == 0) R.string.widget_today_empty else R.string.widget_today_all_done),
                    style = TextStyle(color = ColorProvider(Color(skin.textMuted))),
                )
            }
            tasks.forEach { task -> TaskRow(task, skin) }
        }
    }

    // A failure while drawing goes to the crash log too, before Android's own error box.
    override fun onCompositionError(context: Context, glanceId: GlanceId, appWidgetId: Int, throwable: Throwable) {
        WidgetFallback.log(context, throwable)
        super.onCompositionError(context, glanceId, appWidgetId, throwable)
    }

    @Composable
    private fun TaskRow(task: WidgetTask, skin: WidgetSkin) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable(
                    actionRunCallback<CompleteTaskAction>(actionParametersOf(CompleteTaskAction.TASK_ID to task.id)),
                ),
        ) {
            Text("\u2610", style = TextStyle(color = ColorProvider(Color(skin.textMuted))))
            Spacer(GlanceModifier.width(8.dp))
            Text(
                task.title,
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(Color(skin.text)),
                    fontWeight = if (task.topPriority) FontWeight.Bold else FontWeight.Normal,
                ),
            )
            // A project item says which project, quietly, as the lists' chip does.
            if (task.project.isNotEmpty()) {
                Spacer(GlanceModifier.width(6.dp))
                Text(task.project, maxLines = 1, style = TextStyle(color = ColorProvider(Color(skin.textMuted)), fontSize = 12.sp))
            }
            if (task.time.isNotEmpty()) {
                Spacer(GlanceModifier.width(6.dp))
                Text(task.time, style = TextStyle(color = ColorProvider(Color(skin.textMuted))))
            }
        }
    }
}
