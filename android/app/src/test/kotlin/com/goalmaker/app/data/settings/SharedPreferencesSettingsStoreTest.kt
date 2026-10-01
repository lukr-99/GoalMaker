package com.goalmaker.app.data.settings

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import com.goalmaker.app.domain.settings.Appearance
import com.goalmaker.app.domain.settings.BoardView
import com.goalmaker.app.domain.settings.ComposerMode
import com.goalmaker.app.domain.settings.GoalsView
import com.goalmaker.app.domain.settings.ReduceMotion
import com.goalmaker.app.domain.settings.ThemeMode
import com.goalmaker.app.domain.update.UpdatePostponement
import java.time.Instant
import java.time.LocalTime
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SharedPreferencesSettingsStoreTest {
    private val preferences get() =
        RuntimeEnvironment.getApplication().getSharedPreferences("settings-test", Context.MODE_PRIVATE)

    @Before
    fun setUp() = preferences.edit(commit = true) { clear() }

    @Test
    fun `a fresh install uses the default appearance`() {
        assertEquals(Appearance.DEFAULT, SharedPreferencesSettingsStore(preferences).appearance.value)
    }

    @Test
    fun `the app lock is off until the owner turns it on, and stays on across a restart`() {
        val store = SharedPreferencesSettingsStore(preferences)
        assertEquals(false, store.appLock.value)

        store.setAppLock(true)

        assertEquals(true, store.appLock.value)
        assertEquals(true, SharedPreferencesSettingsStore(preferences).appLock.value)
    }

    @Test
    fun `the pins start as the defaults, keep their order across a restart, and drop what is gone`() {
        val store = SharedPreferencesSettingsStore(preferences)
        assertEquals(listOf("today", "tomorrow", "inbox", "projects"), store.pins.value)

        store.setPins(listOf("habits", "today"))

        assertEquals(listOf("habits", "today"), store.pins.value)
        assertEquals(listOf("habits", "today"), SharedPreferencesSettingsStore(preferences).pins.value)

        preferences.edit(commit = true) { putString("pinned_places", "focus,stats,stats") }
        assertEquals(listOf("stats"), SharedPreferencesSettingsStore(preferences).pins.value)
    }

    @Test
    fun `the composer quick-adds until chat is picked, and remembers the pick across a restart`() {
        val store = SharedPreferencesSettingsStore(preferences)
        assertEquals(ComposerMode.QUICK_ADD, store.composerMode.value)

        store.setComposerMode(ComposerMode.CHAT)

        assertEquals(ComposerMode.CHAT, store.composerMode.value)
        assertEquals(ComposerMode.CHAT, SharedPreferencesSettingsStore(preferences).composerMode.value)

        preferences.edit(commit = true) { putString("composer_mode", "VOICE") }
        assertEquals(ComposerMode.QUICK_ADD, SharedPreferencesSettingsStore(preferences).composerMode.value)
    }

    @Test
    fun `a board shows as columns with Done folded in the list, until changed, and every section can open`() {
        val store = SharedPreferencesSettingsStore(preferences)
        assertEquals(BoardView.COLUMNS, store.boardView.value)
        assertEquals(setOf("done"), store.collapsedColumns.value)

        store.setBoardView(BoardView.LIST)
        store.setCollapsedColumns(emptySet())

        assertEquals(BoardView.LIST, SharedPreferencesSettingsStore(preferences).boardView.value)
        assertEquals(emptySet<String>(), SharedPreferencesSettingsStore(preferences).collapsedColumns.value)

        preferences.edit(commit = true) { putString("board_collapsed_columns", "todo,someday") }
        assertEquals(setOf("todo"), SharedPreferencesSettingsStore(preferences).collapsedColumns.value)
    }

    @Test
    fun `goals show as the ladder until the list is picked, and the pick survives a restart`() {
        val store = SharedPreferencesSettingsStore(preferences)
        assertEquals(GoalsView.LADDER, store.goalsView.value)

        store.setGoalsView(GoalsView.LIST)

        assertEquals(GoalsView.LIST, store.goalsView.value)
        assertEquals(GoalsView.LIST, SharedPreferencesSettingsStore(preferences).goalsView.value)

        preferences.edit(commit = true) { putString("goals_view", "TREE") }
        assertEquals(GoalsView.LADDER, SharedPreferencesSettingsStore(preferences).goalsView.value)
    }

    @Test
    fun `changes apply at once and survive a restart`() {
        val store = SharedPreferencesSettingsStore(preferences)

        store.updateAppearance {
            it.copy(themeId = "night", mode = ThemeMode.DARK, pureBlack = true, reduceMotion = ReduceMotion.ON, completionSound = true)
        }

        val expected = Appearance("night", ThemeMode.DARK, pureBlack = true, reduceMotion = ReduceMotion.ON, completionSound = true)
        assertEquals(expected, store.appearance.value)
        assertEquals(expected, SharedPreferencesSettingsStore(preferences).appearance.value)
    }

    @Test
    fun `the light or dark choice saved before themes existed is kept`() {
        preferences.edit(commit = true) { putString("theme_mode", "LIGHT") }

        val appearance = SharedPreferencesSettingsStore(preferences).appearance.value

        assertEquals(ThemeMode.LIGHT, appearance.mode)
        assertEquals(null, appearance.themeId)
    }

    @Test
    fun `unknown stored values fall back to the defaults`() {
        preferences.edit(commit = true) {
            putString("theme_mode", "SEPIA")
            putString("reduce_motion", "MAYBE")
        }

        val appearance = SharedPreferencesSettingsStore(preferences).appearance.value

        assertEquals(ThemeMode.SYSTEM, appearance.mode)
        assertEquals(ReduceMotion.SYSTEM, appearance.reduceMotion)
    }

    @Test
    fun `the evening reminder is at 20 00 until moved or switched off`() {
        val store = SharedPreferencesSettingsStore(preferences)
        assertEquals(LocalTime.of(20, 0), store.planTomorrowReminder.value)

        store.setPlanTomorrowReminder(LocalTime.of(21, 30))
        assertEquals(LocalTime.of(21, 30), SharedPreferencesSettingsStore(preferences).planTomorrowReminder.value)

        store.setPlanTomorrowReminder(null)
        assertEquals(null, SharedPreferencesSettingsStore(preferences).planTomorrowReminder.value)
    }

    @Test
    fun `the Tally device id is made once and kept across a restart`() {
        val id = SharedPreferencesSettingsStore(preferences).tallyDevice()

        assertEquals(id, UUID.fromString(id).toString())
        assertEquals(id, SharedPreferencesSettingsStore(preferences).tallyDevice())
    }

    @Test
    fun `Tally is off until turned on, and how far it read is kept until cleared`() {
        val store = SharedPreferencesSettingsStore(preferences)
        assertEquals(false, store.tallyOn.value)
        assertEquals(null, store.tallyReadUntil())

        store.setTallyOn(true)
        store.setTallyReadUntil(Instant.parse("2026-09-28T12:00:00Z"))

        val restarted = SharedPreferencesSettingsStore(preferences)
        assertEquals(true, restarted.tallyOn.value)
        assertEquals(Instant.parse("2026-09-28T12:00:00Z"), restarted.tallyReadUntil())

        restarted.setTallyReadUntil(null)
        assertEquals(null, SharedPreferencesSettingsStore(preferences).tallyReadUntil())
    }

    @Test
    fun `the notified update and Later are kept across a restart until cleared`() {
        val store = SharedPreferencesSettingsStore(preferences)
        assertNull(store.updateNotified())
        assertNull(store.updatePostponed())
        val later = UpdatePostponement("1.4.0", Instant.parse("2026-10-04T09:00:00Z"))

        store.setUpdateNotified("1.4.0")
        store.setUpdatePostponed(later)

        val again = SharedPreferencesSettingsStore(preferences)
        assertEquals("1.4.0", again.updateNotified())
        assertEquals(later, again.updatePostponed())
        again.setUpdateNotified(null)
        again.setUpdatePostponed(null)
        assertNull(SharedPreferencesSettingsStore(preferences).updateNotified())
        assertNull(SharedPreferencesSettingsStore(preferences).updatePostponed())
    }
}
