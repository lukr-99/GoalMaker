package com.goalmaker.app.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.GoalHorizon
import java.text.NumberFormat

/**
 * The Goals widget (docs/widgets.md): the Goals screen's four rings, this year, month, week and
 * today, each with how much of its goals is done and "N of M hit", by the same rules as the screen.
 * A tap opens Goals.
 */
class GoalsWidget : GlanceAppWidget() {
    // The rings take the size the widget is given, so it needs to know it exactly.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val shown = WidgetFallback.load(context) { WidgetSkin.of(context) to WidgetData.rings(context) }
        if (shown == null) {
            provideContent { WidgetFallback.Content(context) }
            return
        }
        val (skin, rings) = shown
        val density = context.resources.displayMetrics.density
        val percent = NumberFormat.getPercentInstance(context.resources.configuration.locales[0])
        val track = Color(skin.textMuted).copy(alpha = 0.25f).toArgb()
        provideContent {
            val size = LocalSize.current
            val ringDp = ringSize(size.width.value, size.height.value)
            Column(
                GlanceModifier
                    .fillMaxSize()
                    .background(Color(skin.background))
                    .cornerRadius(20.dp)
                    .padding(PADDING_DP.dp)
                    .clickable(WidgetOpen.goals(context)),
            ) {
                Text(
                    context.getString(R.string.widget_goals_title),
                    maxLines = 1,
                    style = TextStyle(color = ColorProvider(Color(skin.accent)), fontWeight = FontWeight.Bold),
                )
                Spacer(GlanceModifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                ) {
                    rings.forEach { ring ->
                        Ring(ring, ringDp, density, track, skin, percent, context)
                    }
                }
            }
        }
    }

    // A failure while drawing goes to the crash log too, before Android's own error box.
    override fun onCompositionError(context: Context, glanceId: GlanceId, appWidgetId: Int, throwable: Throwable) {
        WidgetFallback.log(context, throwable)
        super.onCompositionError(context, glanceId, appWidgetId, throwable)
    }

    @Composable
    private fun RowScope.Ring(
        ring: WidgetRing,
        ringDp: Float,
        density: Float,
        track: Int,
        skin: WidgetSkin,
        percent: NumberFormat,
        context: Context,
    ) {
        val name = context.getString(labelOf(ring.horizon))
        val hit = context.getString(R.string.goals_ring_hit, ring.hits, ring.total)
        val done = percent.format(ring.fraction)
        val bitmap = RingBitmap.draw(
            ring.fraction,
            track = track,
            color = skin.accent,
            sizePx = (ringDp * density).toInt().coerceAtLeast(1),
            strokePx = ringDp * density * STROKE,
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = GlanceModifier.defaultWeight(),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = GlanceModifier.size(ringDp.dp)) {
                Image(
                    ImageProvider(bitmap),
                    contentDescription = context.getString(R.string.goals_ring_description, name, done, hit),
                    modifier = GlanceModifier.size(ringDp.dp),
                )
                Text(
                    done,
                    maxLines = 1,
                    style = TextStyle(
                        color = ColorProvider(Color(skin.text)),
                        fontSize = (ringDp * 0.24f).coerceIn(9f, 15f).sp,
                        fontWeight = FontWeight.Bold,
                        fontStyle = if (skin.italicHeadings) FontStyle.Italic else FontStyle.Normal,
                    ),
                )
            }
            Spacer(GlanceModifier.height(4.dp))
            Text(
                name,
                maxLines = 1,
                style = TextStyle(color = ColorProvider(Color(skin.text)), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
            )
            Text(
                hit,
                maxLines = 1,
                style = TextStyle(color = ColorProvider(Color(skin.textMuted)), fontSize = 11.sp, textAlign = TextAlign.Center),
            )
        }
    }

    companion object {
        private const val PADDING_DP = 12f

        // The ring's stroke, as a share of its size, about what the Goals screen draws.
        private const val STROKE = 0.11f

        // The header over the rings and the two lines of labels under them.
        private const val HEADER_DP = 26f
        private const val LABELS_DP = 40f

        /** The largest ring that leaves room for four across and the labels below, within reason. */
        fun ringSize(widthDp: Float, heightDp: Float): Float {
            val across = (widthDp - 2 * PADDING_DP) / 4f - 8f
            val down = heightDp - 2 * PADDING_DP - HEADER_DP - LABELS_DP
            return minOf(across, down).coerceIn(28f, 72f)
        }

        private fun labelOf(horizon: GoalHorizon): Int = when (horizon) {
            GoalHorizon.YEAR -> R.string.goals_ring_year
            GoalHorizon.MONTH -> R.string.goals_ring_month
            GoalHorizon.WEEK -> R.string.goals_ring_week
            GoalHorizon.DAY -> R.string.goals_ring_day
        }
    }
}
