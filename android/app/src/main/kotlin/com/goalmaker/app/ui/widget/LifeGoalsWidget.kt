package com.goalmaker.app.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.TimeLeft
import com.goalmaker.app.application.planning.TimeLeftUnit
import kotlin.math.max

/**
 * The Life goals widget (docs/life-goals.md, M9-05): one picture of an open life goal at a time, with
 * its title and time left over it, the next every 30 minutes; a life goal without pictures shows its
 * title and why. A tap opens Life goals on that life goal.
 */
class LifeGoalsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val read = suspend { WidgetFallback.load(context) { WidgetSkin.of(context) to WidgetData.lifeGoal(context) } }
        val first = read()
        provideContent {
            val shown = Widgets.fresh(first, read)
            if (shown == null) WidgetFallback.Content(context) else Content(context, shown.first, shown.second)
        }
    }

    @Composable
    private fun Content(context: Context, skin: WidgetSkin, slide: Pair<WidgetLifeGoal, ByteArray?>?) {
        val goal = slide?.first
        val picture = slide?.second?.let(::decode)
        Box(
            contentAlignment = Alignment.BottomStart,
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color(skin.background))
                .cornerRadius(20.dp)
                .clickable(WidgetOpen.lifeGoal(context, goal?.lifeGoalId)),
        ) {
            when {
                goal == null -> Empty(context, skin)
                picture != null -> {
                    Image(
                        ImageProvider(picture),
                        contentDescription = context.getString(R.string.widget_life_goals_picture, goal.title),
                        contentScale = ContentScale.Crop,
                        modifier = GlanceModifier.fillMaxSize(),
                    )
                    Caption(context, goal, Color.White, Color(0x99000000))
                }
                else -> Words(context, skin, goal)
            }
        }
    }

    // A failure while drawing goes to the crash log too, before Android's own error box.
    override fun onCompositionError(context: Context, glanceId: GlanceId, appWidgetId: Int, throwable: Throwable) {
        WidgetFallback.log(context, throwable)
        super.onCompositionError(context, glanceId, appWidgetId, throwable)
    }

    /** The title and time left on a dark band across the picture's foot. */
    @Composable
    private fun Caption(context: Context, goal: WidgetLifeGoal, text: Color, band: Color) {
        Column(GlanceModifier.fillMaxWidth().background(band).padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(goal.title, maxLines = 1, style = TextStyle(color = ColorProvider(text), fontWeight = FontWeight.Bold, fontSize = 16.sp))
            goal.timeLeft?.let { left ->
                Text(timeLeftText(context, left), maxLines = 1, style = TextStyle(color = ColorProvider(text.copy(alpha = 0.85f)), fontSize = 12.sp))
            }
        }
    }

    /** A life goal without pictures: its title, time left and why on the theme's colors. */
    @Composable
    private fun Words(context: Context, skin: WidgetSkin, goal: WidgetLifeGoal) {
        Column(GlanceModifier.fillMaxSize().background(Color(skin.accent).copy(alpha = 0.16f)).padding(16.dp)) {
            Text(
                goal.title,
                maxLines = 2,
                style = TextStyle(
                    color = ColorProvider(Color(skin.text)),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    fontStyle = if (skin.italicHeadings) FontStyle.Italic else FontStyle.Normal,
                ),
            )
            goal.timeLeft?.let { left ->
                Text(timeLeftText(context, left), maxLines = 1, style = TextStyle(color = ColorProvider(Color(skin.accent)), fontWeight = FontWeight.Bold, fontSize = 12.sp))
            }
            Spacer(GlanceModifier.height(8.dp))
            Text(goal.why, maxLines = 5, style = TextStyle(color = ColorProvider(Color(skin.text)), fontSize = 14.sp))
        }
    }

    @Composable
    private fun Empty(context: Context, skin: WidgetSkin) {
        Column(GlanceModifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                context.getString(R.string.widget_life_goals_title),
                maxLines = 1,
                style = TextStyle(color = ColorProvider(Color(skin.accent)), fontWeight = FontWeight.Bold),
            )
            Text(context.getString(R.string.widget_life_goals_empty), maxLines = 3, style = TextStyle(color = ColorProvider(Color(skin.textMuted))))
        }
    }

    companion object {
        // A widget's bitmaps travel to the launcher, so a picture goes at a widget's size, not its file's.
        private const val WIDGET_SIDE = 720

        /** A picture read at about a widget's size; null when the bytes are not a picture. */
        fun decode(bytes: ByteArray): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= WIDGET_SIDE) sample *= 2
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        }

        fun timeLeftText(context: Context, left: TimeLeft): String = when (left.unit) {
            TimeLeftUnit.YEARS -> context.resources.getQuantityString(R.plurals.life_goals_years_left, left.count, left.count)
            TimeLeftUnit.MONTHS -> context.resources.getQuantityString(R.plurals.life_goals_months_left, left.count, left.count)
            TimeLeftUnit.DAYS -> context.resources.getQuantityString(R.plurals.life_goals_days_left, left.count, left.count)
            TimeLeftUnit.TODAY -> context.getString(R.string.life_goals_today)
            TimeLeftUnit.PAST -> context.getString(R.string.life_goals_past)
        }
    }
}
