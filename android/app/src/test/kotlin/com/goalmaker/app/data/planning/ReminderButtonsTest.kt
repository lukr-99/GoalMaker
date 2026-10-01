package com.goalmaker.app.data.planning

import android.app.Application
import com.goalmaker.app.R
import com.goalmaker.app.domain.planning.Snooze
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The buttons a reminder notification offers, and the snooze each one sends (docs/reminders.md). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ReminderButtonsTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `a fired reminder offers Done, ten minutes and Later`() {
        assertEquals(
            listOf(ReminderAlarm.ACTION_DONE, ReminderAlarm.ACTION_SNOOZE, ReminderAlarm.ACTION_LATER),
            ReminderButtons.first.map { it.action },
        )
        assertEquals(Snooze.TEN_MINUTES, ReminderButtons.first[1].snooze)
    }

    @Test
    fun `Later offers every snooze in the Windows order`() {
        assertEquals(Snooze.entries, ReminderButtons.snoozes.map { it.snooze })
        assertTrue(ReminderButtons.snoozes.all { it.action == ReminderAlarm.ACTION_SNOOZE })
    }

    @Test
    fun `no face shows more buttons than Android does`() {
        assertTrue(ReminderButtons.first.size <= ReminderButtons.MAX)
        assertTrue(ReminderButtons.snoozes.size <= ReminderButtons.MAX)
    }

    @Test
    fun `a snooze survives the trip through the intent`() {
        Snooze.entries.forEach { assertEquals(it, ReminderButtons.snoozeOf(it.name)) }
        assertEquals(Snooze.ONE_HOUR, ReminderButtons.snoozeOf("ONE_HOUR"))
    }

    @Test
    fun `an unknown or missing snooze falls back to ten minutes`() {
        assertEquals(Snooze.TEN_MINUTES, ReminderButtons.snoozeOf(null))
        assertEquals(Snooze.TEN_MINUTES, ReminderButtons.snoozeOf("NEXT_WEEK"))
    }

    @Test
    fun `the snooze labels match the Windows toast`() {
        val windows = windowsStrings()
        assertEquals(windows.getValue("Reminder.SnoozeTenMinutes"), context.getString(R.string.reminder_snooze_ten_minutes))
        assertEquals(windows.getValue("Reminder.SnoozeOneHour"), context.getString(R.string.reminder_snooze_one_hour))
        assertEquals(windows.getValue("Reminder.SnoozeTomorrow"), context.getString(R.string.reminder_snooze_tomorrow))
        assertEquals(windows.getValue("Reminder.Done"), context.getString(R.string.reminder_done))
    }

    private fun windowsStrings(): Map<String, String> {
        val contracts = System.getProperty("goalmaker.contracts") ?: error("Run tests through Gradle")
        val file = File(contracts).parentFile.resolve("windows/src/GoalMaker.App/Resources/Strings.xaml")
        val entry = Regex("""<sys:String x:Key="([^"]+)">([^<]*)</sys:String>""")
        return entry.findAll(file.readText()).associate { it.groupValues[1] to it.groupValues[2] }
    }
}
