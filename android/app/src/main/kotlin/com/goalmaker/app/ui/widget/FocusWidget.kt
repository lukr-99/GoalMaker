package com.goalmaker.app.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.goalmaker.app.R

/**
 * The Focus Board widget (docs/widgets.md): the board's items in priority order, with their date
 * when they have one, as many as the widget's real size holds. The list scrolls when there are more,
 * and a "+N more" line says how many are out of sight. A tap opens the Focus Board in the app.
 */
class FocusWidget : GlanceAppWidget() {
    // The "+N more" line follows the widget's real size, so a resize shows more or fewer rows.
    override val sizeMode = SizeMode.Exact

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
                val fit = WidgetContent.focusFit(tasks.size, LocalSize.current.height.value)
                // The list takes the rest of the widget and scrolls inside it.
                LazyColumn(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    items(tasks, itemId = { it.id.hashCode().toLong() }) { task -> Row(task, skin, context) }
                }
                if (fit.hidden > 0) {
                    Text(
                        context.getString(R.string.widget_focus_more, fit.hidden),
                        maxLines = 1,
                        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp).clickable(WidgetOpen.app(context)),
                        style = TextStyle(color = ColorProvider(Color(skin.accent)), fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
    }

    // Every row opens the app, as the widget's background does: a list's rows do not pass a tap on.
    @Composable
    private fun Row(task: WidgetTask, skin: WidgetSkin, context: Context) {
        val line = if (task.time.isNotBlank()) "${task.title}  ${task.time}" else task.title
        Text(
            line,
            maxLines = 1,
            modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp).clickable(WidgetOpen.app(context)),
            style = TextStyle(color = ColorProvider(Color(skin.text)), fontSize = 14.sp),
        )
    }

    override fun onCompositionError(context: Context, glanceId: GlanceId, appWidgetId: Int, throwable: Throwable) {
        WidgetFallback.log(context, throwable)
        super.onCompositionError(context, glanceId, appWidgetId, throwable)
    }
}
