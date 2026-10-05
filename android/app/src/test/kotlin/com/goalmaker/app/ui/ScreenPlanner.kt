package com.goalmaker.app.ui

import android.content.Context
import androidx.core.content.edit
import com.goalmaker.app.application.assistant.AssistantReply
import com.goalmaker.app.application.auth.AuthSession
import com.goalmaker.app.application.planning.AreaList
import com.goalmaker.app.application.planning.EventList
import com.goalmaker.app.application.planning.GoalList
import com.goalmaker.app.application.planning.HabitList
import com.goalmaker.app.application.planning.NewRows
import com.goalmaker.app.application.planning.ProjectList
import com.goalmaker.app.application.planning.ReminderList
import com.goalmaker.app.application.planning.ReminderScheduler
import com.goalmaker.app.application.planning.ReminderService
import com.goalmaker.app.application.planning.ReviewList
import com.goalmaker.app.application.planning.TagList
import com.goalmaker.app.application.planning.TaskList
import com.goalmaker.app.application.sync.FakeServer
import com.goalmaker.app.application.sync.SyncCoordinator
import com.goalmaker.app.application.sync.SyncEngine
import com.goalmaker.app.application.sync.SyncStatus
import com.goalmaker.app.data.replica.TestReplica
import com.goalmaker.app.data.settings.SharedPreferencesSettingsStore
import com.goalmaker.app.domain.planning.QuietHours
import com.goalmaker.app.ui.calendar.CalendarViewModel
import com.goalmaker.app.ui.chat.ChatViewModel
import com.goalmaker.app.ui.habits.HabitsViewModel
import com.goalmaker.app.ui.lists.ListsViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.robolectric.RuntimeEnvironment

/**
 * Everything a screen test hosts a screen over: a real replica on a throwaway file, the lists over it,
 * fresh settings and a sync that never leaves the test, and the view models the app builds from them.
 * Disk work runs on Unconfined and the clock stands still at noon on Friday 18 September 2026, so a
 * screen shows the same thing every run.
 */
class ScreenPlanner : AutoCloseable {
    private val test = TestReplica()
    private val instant = Instant.parse("2026-09-18T10:00:00Z")
    private val rows = NewRows(test.catalog, { TestReplica.OWNER }, { instant })
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    val now: LocalDateTime = LocalDateTime.parse("2026-09-18T12:00")
    val today: LocalDate = now.toLocalDate()

    val areas = AreaList(test.replica, rows, listOf("violet"), {})
    val tags = TagList(test.replica, rows, {})
    val projects = ProjectList(test.replica, rows, {})
    val tasks = TaskList(test.replica, rows, areas, tags, projects, {}) { today }
    val habits = HabitList(test.replica, rows, {})
    val goals = GoalList(test.replica, rows, {})
    val reminderRows = ReminderList(test.replica, rows, {})
    val events = EventList(test.replica, rows, {})

    val settings = SharedPreferencesSettingsStore(
        RuntimeEnvironment.getApplication().getSharedPreferences("screen-test", Context.MODE_PRIVATE).also { preferences ->
            preferences.edit(commit = true) { clear() }
        },
    )

    private val reminders = ReminderService(
        reminders = reminderRows,
        tasks = tasks,
        scheduler = object : ReminderScheduler {
            override fun armAt(at: LocalDateTime) = Unit

            override fun cancel() = Unit
        },
        quietHours = { QuietHours.OFF },
        dayStartHour = { 4 },
        now = { now },
        remindedUntil = { null },
        setRemindedUntil = {},
    )

    private val sync = SyncCoordinator(
        engine = SyncEngine(test.catalog, test.replica, FakeServer()) { instant },
        replica = test.replica,
        scope = scope,
        io = Dispatchers.Unconfined,
        now = { instant },
        debounce = 2.seconds,
    )

    /** Today, Tomorrow and the Inbox. */
    fun lists() = ListsViewModel(
        tasks,
        areas,
        tags,
        projects,
        goals,
        ReviewList(test.replica, rows, {}),
        habits,
        events,
        settings,
        reminders,
        sync,
        Dispatchers.Unconfined,
    ) { now }

    /** The Habits screen, with the day starting at 04:00. */
    fun habitsPage() = HabitsViewModel(habits, goals, MutableStateFlow(4), Dispatchers.Unconfined) { now }

    /** The calendar. */
    fun calendar() = CalendarViewModel(tasks, reminderRows, areas, tags, projects, habits, events, settings, Dispatchers.Unconfined) { now }

    /** The bottom bar's quick chat, left on quick add; it never reaches a model. */
    fun chat() = ChatViewModel(
        assistant = { AssistantReply.Unavailable },
        settings = settings,
        session = MutableStateFlow(AuthSession.SignedIn(TestReplica.OWNER, "owner@example.com")),
        syncStatus = MutableStateFlow(SyncStatus.INITIAL),
        syncNow = {},
    )

    override fun close() {
        scope.cancel()
        test.close()
    }
}
