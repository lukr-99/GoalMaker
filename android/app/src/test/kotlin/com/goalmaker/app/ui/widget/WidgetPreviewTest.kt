package com.goalmaker.app.ui.widget

import android.app.Application
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.goalmaker.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

/**
 * Every widget shows a preview in the picker on Android 12 and later (docs/widgets.md). Up to
 * 1.9.2 Today and Habits had none, so the picker showed only the app's icon for them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WidgetPreviewTest {
    private val infos = mapOf(
        WidgetKind.TODAY to R.xml.today_widget_info,
        WidgetKind.HABITS to R.xml.habits_widget_info,
        WidgetKind.GOALS to R.xml.goals_widget_info,
        WidgetKind.MOTIVATION to R.xml.motivation_widget_info,
        WidgetKind.QUICK_ADD to R.xml.quick_add_widget_info,
    )

    @Test
    fun `every widget has a preview that inflates in light mode`() = inflateEveryPreview()

    @Test
    @Config(qualifiers = "night")
    fun `every widget has a preview that inflates in dark mode`() = inflateEveryPreview()

    private fun inflateEveryPreview() {
        val context = RuntimeEnvironment.getApplication()
        assertEquals(WidgetKind.entries.toSet(), infos.keys)
        infos.forEach { (kind, xml) ->
            val parser = context.resources.getXml(xml)
            while (parser.next() != XmlPullParser.START_TAG) Unit
            val preview = parser.getAttributeResourceValue(ANDROID, "previewLayout", 0)
            assertNotEquals("$kind has no previewLayout", 0, preview)
            LayoutInflater.from(context).inflate(preview, FrameLayout(context), false)
        }
    }

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
    }
}
