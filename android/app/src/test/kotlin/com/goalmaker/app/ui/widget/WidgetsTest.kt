package com.goalmaker.app.ui.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * Drawing the widgets again goes by the launcher's ids per receiver (docs/widgets.md). In 1.9.0 it
 * went by Glance's `updateAll`, which finds widgets by the drawing class, and R8 had folded the widget
 * classes together, so Today and quick add were drawn over with the Habits list.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WidgetsTest {
    @Test
    fun `each kind draws what its own receiver draws`() {
        WidgetKind.entries.forEach { kind ->
            val receiver = kind.receiver.getDeclaredConstructor().newInstance()
            assertEquals(kind.name, receiver.glanceAppWidget::class.java, kind.create()::class.java)
        }
    }

    @Test
    fun `no two kinds share a receiver or a drawing class`() {
        assertEquals(WidgetKind.entries.size, WidgetKind.entries.map { it.receiver }.toSet().size)
        assertEquals(WidgetKind.entries.size, WidgetKind.entries.map { it.create()::class.java }.toSet().size)
    }

    @Test
    fun `a kind's widgets are the ones placed from its receiver, never another kind's`() {
        val context = RuntimeEnvironment.getApplication()
        val shadow = shadowOf(AppWidgetManager.getInstance(context))
        fun place(id: Int, kind: WidgetKind) = shadow.addBoundWidget(
            id,
            AppWidgetProviderInfo().apply { provider = ComponentName(context, kind.receiver) },
        )
        place(1, WidgetKind.TODAY)
        place(2, WidgetKind.HABITS)
        place(3, WidgetKind.QUICK_ADD)
        place(4, WidgetKind.TODAY)
        place(5, WidgetKind.GOALS)

        assertEquals(listOf(1, 4), Widgets.ids(context, WidgetKind.TODAY).sorted())
        assertEquals(listOf(2), Widgets.ids(context, WidgetKind.HABITS))
        assertEquals(listOf(3), Widgets.ids(context, WidgetKind.QUICK_ADD))
        assertEquals(listOf(5), Widgets.ids(context, WidgetKind.GOALS))
        assertEquals(emptyList<Int>(), Widgets.ids(context, WidgetKind.MOTIVATION))
    }

    @Test
    fun `the release build keeps each widget class apart`() {
        // R8 folds look-alike classes into one unless they are kept; Glance needs their names.
        val rules = listOf(File("proguard-rules.pro"), File("app/proguard-rules.pro")).first(File::exists).readText()
        assertTrue(rules.contains("-keep,allowshrinking class * extends androidx.glance.appwidget.GlanceAppWidget"))
    }
}
