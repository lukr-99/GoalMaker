package com.goalmaker.app.ui.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Each home screen widget by the receiver the manifest names, which is how the launcher knows it,
 * and how to draw it. The app finds a kind's widgets by its receiver, never by the drawing class:
 * a release build's shrinker may fold several widget classes into one (docs/pitfalls.md).
 */
enum class WidgetKind(val receiver: Class<out GlanceAppWidgetReceiver>, val create: () -> GlanceAppWidget) {
    TODAY(TodayWidgetReceiver::class.java, ::TodayWidget),
    HABITS(HabitsWidgetReceiver::class.java, ::HabitsWidget),
    GOALS(GoalsWidgetReceiver::class.java, ::GoalsWidget),
    MOTIVATION(MotivationWidgetReceiver::class.java, ::MotivationWidget),
    QUICK_ADD(QuickAddWidgetReceiver::class.java, ::QuickAddWidget),
}
