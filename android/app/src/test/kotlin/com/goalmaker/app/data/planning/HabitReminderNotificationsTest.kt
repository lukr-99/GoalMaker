package com.goalmaker.app.data.planning

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.goalmaker.app.MainActivity
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.AreaList
import com.goalmaker.app.application.planning.DueHabit
import com.goalmaker.app.application.planning.HabitDraft
import com.goalmaker.app.application.planning.HabitItem
import com.goalmaker.app.application.planning.HabitList
import com.goalmaker.app.application.planning.HabitRules
import com.goalmaker.app.application.planning.NewRows
import com.goalmaker.app.application.planning.ProjectList
import com.goalmaker.app.application.planning.ReminderList
import com.goalmaker.app.application.planning.ReminderScheduler
import com.goalmaker.app.application.planning.ReminderService
import com.goalmaker.app.application.planning.TagList
import com.goalmaker.app.application.planning.TaskList
import com.goalmaker.app.data.replica.TestReplica
import com.goalmaker.app.domain.planning.QuietHours
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * A habit's reminder notification: its channel, what it says, its buttons, and the receiver's path
 * from a button to the check-in or the skip and the notification taken down (docs/reminders.md).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class HabitReminderNotificationsTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val system = context.getSystemService(NotificationManager::class.java)
    private val notifications = ReminderNotifications(context)
    private val day = LocalDate.parse("2026-09-18")
    private lateinit var test: TestReplica
    private lateinit var habits: HabitList
    private lateinit var service: ReminderService

    @Before
    fun setUp() {
        shadowOf(system).setNotificationsEnabled(true)
        notifications.createChannels()
        test = TestReplica()
        val rows = NewRows(test.catalog, { TestReplica.OWNER }, { Instant.parse("2026-09-18T10:00:00Z") })
        val areas = AreaList(test.replica, rows, listOf("violet"), {})
        val tags = TagList(test.replica, rows, {})
        habits = HabitList(test.replica, rows, {})
        service = ReminderService(
            reminders = ReminderList(test.replica, rows, {}),
            tasks = TaskList(test.replica, rows, areas, tags, ProjectList(test.replica, rows, {}), {}) { day },
            scheduler = object : ReminderScheduler {
                override fun armAt(at: LocalDateTime) = Unit

                override fun cancel() = Unit
            },
            quietHours = { QuietHours.OFF },
            dayStartHour = { 4 },
            now = { LocalDateTime.parse("2026-09-18T19:31") },
            remindedUntil = { null },
            setRemindedUntil = {},
            habits = habits,
        )
    }

    @After
    fun tearDown() = test.close()

    private fun add(draft: HabitDraft): HabitItem = habits.add(draft.copy(remindAt = LocalTime.of(19, 30)))!!

    private fun show(habit: HabitItem) = notifications.showHabit(DueHabit(habit, day), habits.read().checkinsOf(habit.id))

    private fun shown(habit: HabitItem): Notification =
        system.activeNotifications.single { it.tag == "${habit.id}/$day" }.notification

    private fun Notification.text(): String = extras.getCharSequence(Notification.EXTRA_TEXT).toString()

    private fun Notification.title(): String = extras.getCharSequence(Notification.EXTRA_TITLE).toString()

    private fun Notification.labels(): List<String> = actions.map { it.title.toString() }

    private fun PendingIntent.intent(): Intent = shadowOf(this).savedIntent

    @Test
    fun `habit reminders have their own channel and the older channels keep their ids`() {
        val habitsChannel = system.getNotificationChannel(ReminderNotifications.CHANNEL_HABITS)
        assertEquals("habits", habitsChannel.id)
        assertEquals(context.getString(R.string.habit_reminder_channel), habitsChannel.name)
        listOf("reminders", "reminders_important", "plan_tomorrow", "reviews", "wants_ready").forEach {
            assertTrue(it, system.getNotificationChannel(it) != null)
        }
    }

    @Test
    fun `a check habit says it is still to do, with Check in and Skip today`() {
        val read = add(HabitDraft("Read", day.minusDays(10), emoji = "📖"))
        show(read)

        val notification = shown(read)
        assertEquals(ReminderNotifications.CHANNEL_HABITS, notification.channelId)
        assertEquals("📖 Read", notification.title())
        assertEquals("Still to do today", notification.text())
        assertEquals(listOf("Check in", "Skip today"), notification.labels())
        val checkIn = notification.actions[0].actionIntent.intent()
        assertEquals(ReminderAlarm.ACTION_HABIT_CHECK_IN, checkIn.action)
        assertEquals(read.id, checkIn.getStringExtra(ReminderAlarm.EXTRA_HABIT_ID))
        assertEquals(day.toString(), checkIn.getStringExtra(ReminderAlarm.EXTRA_HABIT_DAY))
        assertEquals(ReminderAlarm.ACTION_HABIT_SKIP, notification.actions[1].actionIntent.intent().action)
    }

    @Test
    fun `tapping the body opens the Habits place`() {
        val read = add(HabitDraft("Read", day.minusDays(10)))
        show(read)

        val open = shown(read).contentIntent.intent()
        assertEquals(MainActivity::class.java.name, open.component?.className)
        assertTrue(open.getBooleanExtra(ReminderAlarm.EXTRA_OPEN_HABITS, false))
        assertFalse(open.getBooleanExtra(ReminderAlarm.EXTRA_LOG_HABIT, true))
        assertEquals(read.id, open.getStringExtra(ReminderAlarm.EXTRA_HABIT_ID))
    }

    @Test
    fun `a count says how far the day got and adds one`() {
        val water = add(HabitDraft("Water", day.minusDays(10), measure = HabitRules.COUNT, target = 8.0, unit = "glasses", emoji = "💧"))
        habits.checkIn(water.id, day, 4.0)
        show(water)

        val notification = shown(water)
        assertEquals("💧 Water", notification.title())
        assertEquals("4 of 8 glasses today", notification.text())
        assertEquals(listOf("+1", "Skip today"), notification.labels())
        assertEquals(ReminderAlarm.ACTION_HABIT_CHECK_IN, notification.actions[0].actionIntent.intent().action)
    }

    @Test
    fun `an amount's Log opens its log dialog in the app`() {
        val run = add(HabitDraft("Run", day.minusDays(10), measure = HabitRules.AMOUNT, target = 5.0, unit = "km"))
        show(run)

        val notification = shown(run)
        assertEquals("Run", notification.title())
        assertEquals("0 of 5 km today", notification.text())
        assertEquals(listOf("Log", "Skip today"), notification.labels())
        val log = notification.actions[0].actionIntent.intent()
        assertEquals(MainActivity::class.java.name, log.component?.className)
        assertTrue(log.getBooleanExtra(ReminderAlarm.EXTRA_LOG_HABIT, false))
        assertEquals(run.id, log.getStringExtra(ReminderAlarm.EXTRA_HABIT_ID))
    }

    @Test
    fun `a weekly habit counts the week and skips the week`() {
        val swim = add(HabitDraft("Swim", day.minusDays(30), cadence = HabitRules.PER_WEEK, times = 3))
        habits.checkIn(swim.id, day.minusDays(3))
        show(swim)

        val notification = shown(swim)
        assertEquals("1 of 3 this week", notification.text())
        assertEquals(listOf("Check in", "Skip this week"), notification.labels())
    }

    @Test
    fun `Check in through the receiver checks the habit in and takes the notification down`() {
        val read = add(HabitDraft("Read", day.minusDays(10)))
        show(read)
        assertEquals(listOf(read.id to day), notifications.shownHabits())
        assertFalse(service.habitStale(read.id, day))

        assertTrue(HabitReminderButtons.settle(shown(read).actions[0].actionIntent.intent(), service, notifications))

        assertEquals(1.0, habits.read().checkinsOf(read.id).single { it.day == day }.value, 0.0)
        assertTrue(service.habitStale(read.id, day))
        assertEquals(emptyList<Pair<String, LocalDate>>(), notifications.shownHabits())
    }

    @Test
    fun `+1 through the receiver adds one to the count`() {
        val water = add(HabitDraft("Water", day.minusDays(10), measure = HabitRules.COUNT, target = 8.0))
        habits.checkIn(water.id, day, 4.0)
        show(water)

        HabitReminderButtons.settle(shown(water).actions[0].actionIntent.intent(), service, notifications)

        assertEquals(5.0, habits.read().checkinsOf(water.id).single { it.day == day }.value, 0.0)
        assertTrue(notifications.shownHabits().isEmpty())
    }

    @Test
    fun `Skip through the receiver skips the period and takes the notification down`() {
        val read = add(HabitDraft("Read", day.minusDays(10)))
        show(read)

        HabitReminderButtons.settle(shown(read).actions[1].actionIntent.intent(), service, notifications)

        assertTrue(habits.read().checkinsOf(read.id).single { it.day == day }.skipped)
        assertTrue(service.habitStale(read.id, day))
        assertTrue(notifications.shownHabits().isEmpty())
    }

    @Test
    fun `an intent without a habit or a day settles nothing`() {
        val read = add(HabitDraft("Read", day.minusDays(10)))
        val noDay = ReminderAlarm.intent(context, ReminderAlarm.ACTION_HABIT_CHECK_IN).putExtra(ReminderAlarm.EXTRA_HABIT_ID, read.id)

        assertFalse(HabitReminderButtons.settle(noDay, service, notifications))
        assertTrue(habits.read().checkinsOf(read.id).isEmpty())
    }

    @Test
    fun `nothing shows while notifications are off`() {
        shadowOf(system).setNotificationsEnabled(false)
        show(add(HabitDraft("Read", day.minusDays(10))))

        assertTrue(system.activeNotifications.isEmpty())
    }

    @Test
    fun `the button labels match the Windows toast`() {
        val contracts = System.getProperty("goalmaker.contracts") ?: error("Run tests through Gradle")
        val file = File(contracts).parentFile.resolve("windows/src/GoalMaker.App/Resources/Strings.xaml")
        val entry = Regex("""<sys:String x:Key="([^"]+)">([^<]*)</sys:String>""")
        val windows = entry.findAll(file.readText()).associate { it.groupValues[1] to it.groupValues[2] }
        assertEquals(windows.getValue("HabitReminder.CheckIn"), context.getString(R.string.habit_reminder_check_in))
        assertEquals(windows.getValue("HabitReminder.AddOne"), context.getString(R.string.habit_reminder_add_one))
        assertEquals(windows.getValue("HabitReminder.Log"), context.getString(R.string.habit_reminder_log))
        assertEquals(windows.getValue("HabitReminder.Left"), context.getString(R.string.habit_reminder_left))
        assertEquals(windows.getValue("Habits.SkipDay"), context.getString(R.string.habits_skip_day))
        assertEquals(windows.getValue("Habits.SkipWeek"), context.getString(R.string.habits_skip_week))
        assertEquals(windows.getValue("Habits.SkipMonth"), context.getString(R.string.habits_skip_month))
    }
}
