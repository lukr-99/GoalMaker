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
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.goalmaker.app.MotivationConfigureActivity
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.GoalHorizon

/**
 * The Motivation widget (docs/widgets.md): the owner's own words, or the goals of this week, month or
 * year as plain lines, in large calm type that shrinks to fit however the widget is sized. Each
 * widget keeps its own setup ([MotivationStore]). A tap opens Goals when it shows goals, and the
 * configure screen when it shows the owner's words, so they can change them.
 */
class MotivationWidget : GlanceAppWidget() {
    // The type size follows the widget's real size, so it needs to know it exactly.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val read = suspend {
            WidgetFallback.load(context) {
                val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
                val choice = MotivationStore.of(context).read(widgetId)
                val lines = if (choice.mode == MotivationMode.GOALS) {
                    WidgetData.goalLines(context, choice.horizon)
                } else {
                    choice.text.lines().map(String::trim).filter(String::isNotEmpty)
                }
                val allDone = choice.mode == MotivationMode.GOALS && WidgetData.goalsAllDone(context, choice.horizon)
                Shown(WidgetSkin.of(context), widgetId, choice, lines, allDone)
            }
        }
        val first = read()
        provideContent {
            val shown = Widgets.fresh(first, read)
            if (shown == null) WidgetFallback.Content(context) else Content(context, shown)
        }
    }

    @Composable
    private fun Content(context: Context, shown: Shown) {
        val (skin, widgetId, choice, lines, allDone) = shown
        val goals = choice.mode == MotivationMode.GOALS
        val header = if (goals) context.getString(headerOf(choice.horizon)) else null
        val empty = when {
            lines.isNotEmpty() -> null
            goals && allDone -> context.getString(R.string.widget_motivation_all_done)
            goals -> context.getString(R.string.widget_motivation_no_goals)
            else -> context.getString(R.string.widget_motivation_no_text)
        }
        val tap = if (goals) {
            WidgetOpen.goals(context)
        } else {
            actionStartActivity(MotivationConfigureActivity.intent(context, widgetId))
        }
        val size = LocalSize.current
        val headerSpace = if (header != null) HEADER_DP else 0f
        val words = if (empty != null) listOf(empty) else lines
        val fontSize = MotivationFit.size(
            words,
            widthDp = size.width.value - 2 * PADDING_DP,
            heightDp = size.height.value - 2 * PADDING_DP - headerSpace,
        )
        Column(
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = if (goals) Alignment.Start else Alignment.CenterHorizontally,
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color(skin.background))
                .cornerRadius(20.dp)
                .padding(PADDING_DP.dp)
                .clickable(tap),
        ) {
            if (header != null) {
                Text(
                    header,
                    maxLines = 1,
                    style = TextStyle(color = ColorProvider(Color(skin.accent)), fontWeight = FontWeight.Bold),
                )
                Spacer(GlanceModifier.height(6.dp))
            }
            Text(
                words.joinToString("\n"),
                modifier = GlanceModifier.fillMaxWidth(),
                style = TextStyle(
                    color = ColorProvider(Color(if (empty != null) skin.textMuted else skin.text)),
                    fontSize = (if (empty != null) MotivationFit.MIN + 2f else fontSize).sp,
                    fontWeight = if (empty != null) FontWeight.Normal else FontWeight.Medium,
                    fontStyle = if (skin.italicHeadings && empty == null) FontStyle.Italic else FontStyle.Normal,
                    textAlign = if (goals) TextAlign.Start else TextAlign.Center,
                ),
            )
        }
    }

    companion object {
        private const val PADDING_DP = 16f

        // The header line and the gap under it.
        private const val HEADER_DP = 26f

        fun headerOf(horizon: GoalHorizon): Int = when (horizon) {
            GoalHorizon.YEAR -> R.string.widget_motivation_year
            GoalHorizon.MONTH -> R.string.widget_motivation_month
            else -> R.string.widget_motivation_week
        }

        /** Draws one Motivation widget again, after its configure screen saved. */
        suspend fun update(context: Context, widgetId: Int) {
            if (widgetId in Widgets.ids(context, WidgetKind.MOTIVATION)) {
                Widgets.redraw(context, WidgetKind.MOTIVATION, widgetId)
            } else {
                Widgets.redraw(context, WidgetKind.MOTIVATION)
            }
        }
    }

    // A failure while drawing goes to the crash log too, before Android's own error box.
    override fun onCompositionError(context: Context, glanceId: GlanceId, appWidgetId: Int, throwable: Throwable) {
        WidgetFallback.log(context, throwable)
        super.onCompositionError(context, glanceId, appWidgetId, throwable)
    }

    /** What one widget shows, read before it is drawn. */
    private data class Shown(
        val skin: WidgetSkin,
        val widgetId: Int,
        val choice: MotivationChoice,
        val lines: List<String>,
        /** The goals mode's horizon has goals and all of them are done, so it says that rather than "none". */
        val allDone: Boolean = false,
    )
}
