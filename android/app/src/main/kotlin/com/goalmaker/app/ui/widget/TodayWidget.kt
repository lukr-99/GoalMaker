package com.goalmaker.app.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
        val skin = WidgetSkin.of(context)
        val tasks = WidgetData.today(context)
        val (done, total) = WidgetData.doneToday(context)
        provideContent {
            Column(
                GlanceModifier
                    .fillMaxSize()
                    .background(Color(skin.background))
                    .cornerRadius(20.dp)
                    .padding(12.dp)
                    .clickable(WidgetOpen.app(context)),
            ) {
                Text(
                    context.getString(R.string.widget_today_header, done, total),
                    style = TextStyle(color = ColorProvider(Color(skin.accent)), fontWeight = FontWeight.Bold),
                )
                Spacer(GlanceModifier.padding(top = 6.dp))
                if (tasks.isEmpty()) {
                    Text(
                        context.getString(R.string.widget_today_empty),
                        style = TextStyle(color = ColorProvider(Color(skin.textMuted))),
                    )
                }
                tasks.forEach { task -> TaskRow(task, skin) }
            }
        }
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
            if (task.time.isNotEmpty()) {
                Spacer(GlanceModifier.width(6.dp))
                Text(task.time, style = TextStyle(color = ColorProvider(Color(skin.textMuted))))
            }
        }
    }
}
