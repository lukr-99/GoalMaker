package com.goalmaker.app.ui.widget

import android.app.Application
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.provideContent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Lays the widgets out the way a launcher does (RemoteViews applied to a view of the widget's size),
 * so a row that is composed but pushed out of sight fails here. In 1.9.1 a Spacer with only padding
 * took the whole widget, and Today and Habits showed their header over an empty card.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@OptIn(ExperimentalGlanceApi::class)
class WidgetLayoutTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val skin = WidgetSkin(
        background = 0xFF15121F.toInt(),
        surface = 0xFF1F1B2E.toInt(),
        text = 0xFFF2EEFF.toInt(),
        textMuted = 0xFFB0A8C8.toInt(),
        accent = 0xFFFF7A59.toInt(),
    )

    @Test
    fun `the Today widget shows its tasks under the header`() = runTest {
        val tasks = listOf(
            WidgetTask("1", "Review the widget bug report", topPriority = true),
            WidgetTask("2", "Dentist", time = "15:30"),
            WidgetTask("3", "Buy oat milk"),
        )
        val root = layOut { TodayWidget().Content(context, skin, tasks, done = 1, total = 4) }

        listOf("Review the widget bug report", "Dentist", "15:30", "Buy oat milk").forEach { assertVisible(root, it) }
    }

    @Test
    fun `the Today widget says so when nothing is left`() = runTest {
        val root = layOut { TodayWidget().Content(context, skin, emptyList(), done = 0, total = 0) }

        assertVisible(root, "Today")
        assertVisible(root, "Nothing planned today.")
        assertTrue("an empty day has no count", texts(root).none { it.text.contains(" of ") })
    }

    @Test
    fun `the Today widget says the day is done when every task is`() = runTest {
        val root = layOut { TodayWidget().Content(context, skin, emptyList(), done = 3, total = 3) }

        assertVisible(root, "Today · 3 of 3 done")
        assertVisible(root, "All done for today.")
    }

    @Test
    fun `the Habits widget shows its habits under the header`() = runTest {
        val habits = listOf(
            WidgetHabit("1", "Meditate", "🧘", ring = 0.0, done = false),
            WidgetHabit("2", "Drink water", "💧", ring = 0.4, done = false, count = "3/8"),
            WidgetHabit("3", "Coffee", "☕", ring = 0.5, done = false, left = false, count = "1/2"),
        )
        val root = layOut { HabitsWidget().Content(context, skin, habits) }

        listOf("Meditate", "Drink water", "3/8", "Coffee", "1/2").forEach { assertVisible(root, it) }
    }

    @Test
    fun `the Habits widget says so when there are no habits today`() = runTest {
        val root = layOut { HabitsWidget().Content(context, skin, emptyList()) }

        assertVisible(root, context.getString(com.goalmaker.app.R.string.widget_habits_empty))
    }

    /** Composes [content] at the Today widget's default 3 by 3 size and lays it out like a launcher. */
    private suspend fun layOut(content: @Composable () -> Unit): ViewGroup {
        val widget = object : GlanceAppWidget() {
            override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent { content() }
        }
        val size = DpSize(250.dp, 250.dp)
        val views = widget.compose(context, size = size)
        val density = context.resources.displayMetrics.density
        val width = (size.width.value * density).toInt()
        val height = (size.height.value * density).toInt()
        val host = FrameLayout(context)
        host.addView(views.apply(context, host))
        host.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        host.layout(0, 0, width, height)
        return host
    }

    private fun assertVisible(root: ViewGroup, text: String) {
        val view = texts(root).firstOrNull { it.text.toString() == text }
        assertTrue("'$text' is not drawn at all", view != null)
        val place = view!!.positionIn(root)
        val inside = view!!.visibility == View.VISIBLE && view.height > 0 && place[1] + view.height <= root.height
        assertTrue("'$text' is drawn out of sight (top ${place[1]}, height ${view.height}, widget ${root.height})", inside)
    }

    private fun texts(group: ViewGroup): List<TextView> = (0 until group.childCount).flatMap { index ->
        when (val child = group.getChildAt(index)) {
            is TextView -> listOf(child)
            is ViewGroup -> texts(child)
            else -> emptyList()
        }
    }

    /** The view's top left corner relative to [root], from the laid-out positions. */
    private fun View.positionIn(root: ViewGroup): IntArray {
        var x = 0
        var y = 0
        var view: View = this
        while (view !== root) {
            x += view.left
            y += view.top
            view = view.parent as View
        }
        return intArrayOf(x, y)
    }
}
