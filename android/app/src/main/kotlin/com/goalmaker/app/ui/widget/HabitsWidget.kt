package com.goalmaker.app.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.GridCells
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.LazyVerticalGrid
import androidx.glance.appwidget.lazy.items
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
 * The Habits widget (spec, story 86): today's habits, one tap to check one in. A habit measured by an
 * amount asks for its value, so it opens the app instead of guessing. The habits scroll when there
 * are more than fit, and a wide widget lays them out as two columns of compact tiles
 * ([WidgetContent.habitColumns]).
 */
class HabitsWidget : GlanceAppWidget() {
    // The layout follows the widget's real size, so a resize can switch between one and two columns.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val read = suspend { WidgetFallback.load(context) { WidgetSkin.of(context) to WidgetData.habits(context) } }
        val first = read()
        provideContent {
            val shown = Widgets.fresh(first, read)
            if (shown == null) WidgetFallback.Content(context) else Content(context, shown.first, shown.second)
        }
    }

    /** What the widget draws, apart from reading it, so a test can lay it out. */
    @Composable
    internal fun Content(context: Context, skin: WidgetSkin, habits: List<WidgetHabit>) {
        val left = WidgetContent.habitsLeft(habits)
        val size = LocalSize.current
        val columns = WidgetContent.habitColumns(habits.size, size.width.value, size.height.value)
        Column(
            GlanceModifier
                .fillMaxSize()
                .background(Color(skin.background))
                .cornerRadius(20.dp)
                .padding(12.dp)
                .clickable(WidgetOpen.app(context)),
        ) {
            Text(
                if (habits.isEmpty()) {
                    context.getString(R.string.widget_habits_title)
                } else {
                    context.getString(R.string.widget_habits_header, left)
                },
                style = TextStyle(color = ColorProvider(Color(skin.accent)), fontWeight = FontWeight.Bold),
            )
            // A Spacer needs a size: with only padding, Glance stretches it over the rest of the
            // widget and the rows below go out of sight (docs/pitfalls.md).
            Spacer(GlanceModifier.height(6.dp))
            if (habits.isEmpty()) {
                Text(
                    context.getString(R.string.widget_habits_empty),
                    style = TextStyle(color = ColorProvider(Color(skin.textMuted))),
                )
            } else if (columns == 2) {
                // The list takes the rest of the widget and scrolls inside it.
                LazyVerticalGrid(GridCells.Fixed(2), GlanceModifier.fillMaxWidth().defaultWeight()) {
                    items(habits, itemId = { it.id.hashCode().toLong() }) { habit -> HabitRow(habit, skin, context, compact = true) }
                }
            } else {
                LazyColumn(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    items(habits, itemId = { it.id.hashCode().toLong() }) { habit -> HabitRow(habit, skin, context, compact = false) }
                }
            }
        }
    }

    // A failure while drawing goes to the crash log too, before Android's own error box.
    override fun onCompositionError(context: Context, glanceId: GlanceId, appWidgetId: Int, throwable: Throwable) {
        WidgetFallback.log(context, throwable)
        super.onCompositionError(context, glanceId, appWidgetId, throwable)
    }

    /** One habit: a full row, or a [compact] tile with tighter spacing for the two-column grid. */
    @Composable
    internal fun HabitRow(habit: WidgetHabit, skin: WidgetSkin, context: Context, compact: Boolean) {
        val gap = if (compact) 4.dp else 8.dp
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = if (compact) 2.dp else 0.dp)
                .clickable(
                    if (habit.tappable) {
                        actionRunCallback<CheckInHabitAction>(actionParametersOf(CheckInHabitAction.HABIT_ID to habit.id))
                    } else {
                        WidgetOpen.app(context)
                    },
                ),
        ) {
            Text(
                if (habit.done) "\u25CF" else "\u25CB",
                style = TextStyle(color = ColorProvider(Color(if (habit.done) skin.accent else skin.textMuted))),
            )
            Spacer(GlanceModifier.width(gap))
            if (habit.emoji.isNotEmpty()) {
                Text(habit.emoji, style = TextStyle(color = ColorProvider(Color(skin.text))))
                Spacer(GlanceModifier.width(4.dp))
            }
            Text(
                habit.name,
                maxLines = 1,
                // A tile gives the name what is left, so a long one is cut and the count still shows.
                modifier = if (compact) GlanceModifier.defaultWeight() else GlanceModifier,
                style = TextStyle(color = ColorProvider(Color(if (habit.done) skin.textMuted else skin.text))),
            )
            if (habit.count.isNotEmpty()) {
                Spacer(GlanceModifier.width(if (compact) 4.dp else 6.dp))
                Text(habit.count, style = TextStyle(color = ColorProvider(Color(skin.textMuted))))
            }
        }
    }
}
