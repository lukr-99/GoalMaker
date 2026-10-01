package com.goalmaker.app.ui.widget

import android.app.Application
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** A widget whose rows cannot be read shows a plain card and leaves the error in the crash log. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WidgetFallbackTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `what loads is passed through`() {
        assertEquals(3, runBlocking { WidgetFallback.load(context) { 3 } })
    }

    @Test
    fun `a failure gives the fallback and is written to the crash log`() {
        val log = File(context.filesDir, "crash.log").apply { delete() }

        val shown = runBlocking { WidgetFallback.load<Int>(context) { error("the replica would not open") } }

        assertNull(shown)
        assertTrue(log.readText().contains("the replica would not open"))
    }

    @Test(expected = CancellationException::class)
    fun `a cancelled draw stays cancelled`() {
        runBlocking { WidgetFallback.load<Int>(context) { throw CancellationException("gone") } }
    }
}
