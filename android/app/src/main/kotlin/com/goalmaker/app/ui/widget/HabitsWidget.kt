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
 * The Habits widget (spec, story 86): today's habits, one tap to check one in. A habit measured by an
 * amount asks for its value, so it opens the app instead of guessing.
 */
class HabitsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val skin = WidgetSkin.of(context)
        val habits = WidgetData.habits(context)
        val left = WidgetContent.habitsLeft(habits)
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
                    if (habits.isEmpty()) {
                        context.getString(R.string.widget_habits_title)
                    } else {
                        context.getString(R.string.widget_habits_header, left)
                    },
                    style = TextStyle(color = ColorProvider(Color(skin.accent)), fontWeight = FontWeight.Bold),
                )
                Spacer(GlanceModifier.padding(top = 6.dp))
                if (habits.isEmpty()) {
                    Text(
                        context.getString(R.string.widget_habits_empty),
                        style = TextStyle(color = ColorProvider(Color(skin.textMuted))),
                    )
                }
                habits.forEach { habit -> HabitRow(habit, skin, context) }
            }
        }
    }

    @Composable
    private fun HabitRow(habit: WidgetHabit, skin: WidgetSkin, context: Context) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
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
            Spacer(GlanceModifier.width(8.dp))
            if (habit.emoji.isNotEmpty()) {
                Text(habit.emoji, style = TextStyle(color = ColorProvider(Color(skin.text))))
                Spacer(GlanceModifier.width(4.dp))
            }
            Text(
                habit.name,
                maxLines = 1,
                style = TextStyle(color = ColorProvider(Color(if (habit.done) skin.textMuted else skin.text))),
            )
            if (habit.count.isNotEmpty()) {
                Spacer(GlanceModifier.width(6.dp))
                Text(habit.count, style = TextStyle(color = ColorProvider(Color(skin.textMuted))))
            }
        }
    }
}
