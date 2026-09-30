package com.goalmaker.app.ui.settings

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import com.goalmaker.app.application.planning.FakeUsageSource
import com.goalmaker.app.application.planning.NewRows
import com.goalmaker.app.application.planning.TallyList
import com.goalmaker.app.application.planning.TallyTracker
import com.goalmaker.app.data.replica.TestReplica
import com.goalmaker.app.data.settings.SharedPreferencesSettingsStore
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Tally's switch and the usage access card in Settings (docs/tally.md, M8-11). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TallyViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val usage = FakeUsageSource()
    private val now = Instant.parse("2026-09-28T12:00:00Z")
    private lateinit var test: TestReplica
    private lateinit var settings: SharedPreferencesSettingsStore
    private lateinit var tally: TallyList
    private lateinit var tracker: TallyTracker

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        test = TestReplica()
        val preferences = RuntimeEnvironment.getApplication().getSharedPreferences("tally-view-test", Context.MODE_PRIVATE)
        preferences.edit(commit = true) { clear() }
        settings = SharedPreferencesSettingsStore(preferences)
        tally = TallyList(test.replica, NewRows(test.catalog, { TestReplica.OWNER }, { now }), {}) { "phone" }
        tracker = TallyTracker(usage, tally, emptyList(), settings, { now }, { ZoneOffset.UTC })
        usage.add("com.google.android.youtube", "2026-09-28T10:00:00Z", "2026-09-28T10:30:00Z")
    }

    @After
    fun tearDown() {
        test.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `Tally is off until turned on, and on without access the card asks for it`() {
        usage.granted = false
        val viewModel = TallyViewModel(tracker, dispatcher)
        assertEquals(TallyUiState(on = false, granted = false), viewModel.uiState.value)
        assertFalse(viewModel.uiState.value.askForAccess)

        viewModel.setOn(true)

        assertTrue(settings.tallyOn.value)
        assertTrue(viewModel.uiState.value.askForAccess)
        assertEquals(emptyList<Any>(), tally.totals(day, day))
    }

    @Test
    fun `access granted on the system's page is seen on return and counts at once`() {
        usage.granted = false
        val viewModel = TallyViewModel(tracker, dispatcher)
        viewModel.setOn(true)

        usage.granted = true
        viewModel.checkAccess()

        assertFalse(viewModel.uiState.value.askForAccess)
        assertEquals(listOf("other" to 30), tally.totals(day, day).map { it.category to it.minutes })
    }

    @Test
    fun `access taken away brings the card back, and turning off takes it down`() {
        val viewModel = TallyViewModel(tracker, dispatcher)
        viewModel.setOn(true)
        assertFalse(viewModel.uiState.value.askForAccess)

        usage.granted = false
        viewModel.checkAccess()
        assertTrue(viewModel.uiState.value.askForAccess)

        viewModel.setOn(false)
        assertFalse(viewModel.uiState.value.askForAccess)
        assertFalse(settings.tallyOn.value)
    }

    private companion object {
        val day: LocalDate = LocalDate.parse("2026-09-28")
    }
}
