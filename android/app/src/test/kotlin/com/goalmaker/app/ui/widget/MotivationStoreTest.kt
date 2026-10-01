package com.goalmaker.app.ui.widget

import android.app.Application
import android.content.Context
import com.goalmaker.app.application.planning.GoalHorizon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Each Motivation widget's own setup, kept on the device per widget (docs/widgets.md). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MotivationStoreTest {
    private lateinit var store: MotivationStore

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        store = MotivationStore(context.getSharedPreferences("motivation_test", Context.MODE_PRIVATE))
    }

    @Test
    fun `a widget with no setup shows this week's goals`() {
        assertEquals(MotivationChoice(MotivationMode.GOALS, "", GoalHorizon.WEEK), store.read(7))
    }

    @Test
    fun `the owner's text is kept per widget, lines and all`() {
        store.write(7, MotivationChoice(MotivationMode.TEXT, "Free time is not wasted time.\nRest counts.", GoalHorizon.WEEK))
        store.write(8, MotivationChoice(MotivationMode.GOALS, "", GoalHorizon.YEAR))

        assertEquals(MotivationChoice(MotivationMode.TEXT, "Free time is not wasted time.\nRest counts.", GoalHorizon.WEEK), store.read(7))
        assertEquals(MotivationChoice(MotivationMode.GOALS, "", GoalHorizon.YEAR), store.read(8))
    }

    @Test
    fun `the text is trimmed and kept short`() {
        store.write(7, MotivationChoice(MotivationMode.TEXT, "  " + "a".repeat(MotivationChoice.MAX_TEXT + 50) + "  "))

        assertEquals(MotivationChoice.MAX_TEXT, store.read(7).text.length)
    }

    @Test
    fun `a removed widget forgets its setup`() {
        store.write(7, MotivationChoice(MotivationMode.TEXT, "Keep going", GoalHorizon.MONTH))

        store.forget(7)

        assertEquals(MotivationChoice(), store.read(7))
    }

    @Test
    fun `a day horizon is never shown, so it falls back to the week`() {
        store.write(7, MotivationChoice(MotivationMode.GOALS, "", GoalHorizon.DAY))

        assertEquals(GoalHorizon.WEEK, store.read(7).horizon)
    }

    @Test
    fun `text mode needs words before it can be saved`() {
        assertFalse(MotivationChoice(MotivationMode.TEXT, "   ").ready)
        assertTrue(MotivationChoice(MotivationMode.TEXT, "Keep going").ready)
        assertTrue(MotivationChoice(MotivationMode.GOALS).ready)
    }

    @Test
    fun `a short phrase is drawn large and a long list small`() {
        val short = MotivationFit.size(listOf("Keep going"), widthDp = 250f, heightDp = 110f)
        val long = MotivationFit.size(List(8) { "A goal with a fairly long title $it" }, widthDp = 250f, heightDp = 110f)

        assertEquals(MotivationFit.MAX, short, 0f)
        assertTrue(long < short)
        assertTrue(long >= MotivationFit.MIN)
    }

    @Test
    fun `the size shrinks until the lines fit the widget`() {
        val lines = listOf("Free time is not wasted time")
        val size = MotivationFit.size(lines, widthDp = 180f, heightDp = 60f)

        assertTrue(MotivationFit.rows(lines, 180f, size) * size * 1.3f <= 60f)
        assertTrue(MotivationFit.rows(lines, 180f, size + 1f) * (size + 1f) * 1.3f > 60f)
    }
}
