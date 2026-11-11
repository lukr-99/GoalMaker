package com.goalmaker.app.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.goalmaker.app.R

/**
 * The Focus Board widget (docs/widgets.md): up to [WidgetContent.ROWS] board items in priority
 * order, with their date when they have one. A tap opens the Focus Board in the app.
 */
class FocusWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val read = suspend { WidgetFallback.load(context) { WidgetSkin.of(context) to WidgetData.focusBoard(context) } }
        val first = read()
        provideContent {
            val shown = Widgets.fresh(first, read)
            if (shown == null) WidgetFallback.Content(context) else Content(context, shown.first, shown.second)
        }
    }

    @Composable
    internal fun Content(context: Context, skin: WidgetSkin, tasks: List<WidgetTask>) {
        Column(
            GlanceModifier
                .fillMaxSize()
                .background(Color(skin.background))
                .cornerRadius(20.dp)
                .padding(12.dp)
                .clickable(WidgetOpen.app(context)),
        ) {
            Text(
                context.getString(R.string.widget_focus_title),
                style = TextStyle(color = ColorProvider(Color(skin.accent)), fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.height(6.dp))
            if (tasks.isEmpty()) {
                Text(
                    context.getString(R.string.widget_focus_empty),
                    style = TextStyle(color = ColorProvider(Color(skin.textMuted))),
                )
            } else {
                tasks.forEach { task ->
                    val line = if (task.time.isNotBlank()) "${task.title}  ${task.time}" else task.title
                    Text(
                        line,
                        maxLines = 1,
                        style = TextStyle(
                            color = ColorProvider(Color(skin.text)),
                            fontSize = 14.sp,
                        ),
                    )
                    Spacer(GlanceModifier.height(2.dp))
                }
            }
        }
    }

    override fun onCompositionError(context: Context, glanceId: GlanceId, appWidgetId: Int, throwable: Throwable) {
        WidgetFallback.log(context, throwable)
        super.onCompositionError(context, glanceId, appWidgetId, throwable)
    }
}
