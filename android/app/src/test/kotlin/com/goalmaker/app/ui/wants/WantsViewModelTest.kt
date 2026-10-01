package com.goalmaker.app.ui.wants

import android.app.Application
import android.os.Looper
import com.goalmaker.app.application.planning.NewRows
import com.goalmaker.app.application.planning.WantCooldowns
import com.goalmaker.app.application.planning.WantDraft
import com.goalmaker.app.application.planning.WantList
import com.goalmaker.app.application.planning.WantRules
import com.goalmaker.app.application.planning.WantState
import com.goalmaker.app.data.replica.TestReplica
import com.goalmaker.app.ui.composer.LineOutcome
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The Wants place over a real replica: filters, rings, deciding with undo, thresholds (M8-04). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WantsViewModelTest {
    private lateinit var test: TestReplica
    private lateinit var wants: WantList
    private lateinit var viewModel: WantsViewModel

    // Monday 5 October 2026, 02:00: with the day starting at 04:00 it is still Sunday the 4th.
    private val now = LocalDateTime.parse("2026-10-05T02:00")
    private var added = LocalDate.parse("2026-09-28")

    @Before
    fun setUp() {
        test = TestReplica()
        val rows = NewRows(test.catalog, { TestReplica.OWNER }, { Instant.parse("2026-10-05T00:00:00Z") })
        wants = WantList(test.replica, rows, {}) { added }
        viewModel = WantsViewModel(wants, MutableStateFlow(4), Dispatchers.Unconfined) { now }
    }

    @After
    fun tearDown() = test.close()

    @Test
    fun `cooling is shown while nothing is ready, and the ring counts the days down`() = runTest {
        wants.add(WantDraft("Lamp", "Dark desk", price = 450.0))!!

        val state = viewModel.uiState.first { it.loaded && it.rows.isNotEmpty() }

        assertEquals(WantState.COOLING, state.filter)
        val row = state.rows.single()
        assertEquals(1, row.daysLeft)
        assertEquals(6.0 / 7.0, row.progress, 1e-9)
        assertEquals(mapOf(WantState.READY to 0, WantState.COOLING to 1, WantState.DECIDED to 0), state.counts)
    }

    @Test
    fun `ready leads once anything is ready, and the owner's filter sticks`() = runTest {
        wants.add(WantDraft("Kindle", "Reading at night", price = 3290.0, pickedDays = 3))!!
        wants.add(WantDraft("Desk", "Back pain", price = 12900.0))!!

        val ready = viewModel.uiState.first { it.counts[WantState.READY] == 1 }
        assertEquals(WantState.READY, ready.filter)
        assertEquals(listOf("Kindle"), ready.rows.map { it.want.title })

        viewModel.show(WantState.COOLING)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("Desk"), viewModel.uiState.first { it.filter == WantState.COOLING }.rows.map { it.want.title })
    }

    @Test
    fun `deciding moves a want to Decided and undo takes it back`() = runTest {
        val kindle = wants.add(WantDraft("Kindle", "Reading at night", pickedDays = 0))!!
        val undo = mutableListOf<WantUndo>()
        val collecting = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined).launchCollect(viewModel, undo)

        viewModel.decide(kindle, WantRules.DROPPED, "Library card works")
        viewModel.show(WantState.DECIDED)
        shadowOf(Looper.getMainLooper()).idle()
        val decided = viewModel.uiState.first { it.filter == WantState.DECIDED && it.rows.isNotEmpty() }
        assertEquals("Library card works", decided.rows.single().want.decisionNote)

        assertEquals(WantUndo.Kind.DROPPED, undo.single().kind)
        undo.single().undo()
        assertEquals(null, wants.get(kindle.id)!!.decision)
        collecting.cancel()
    }

    @Test
    fun `the add sheet's cooldown follows the thresholds, and new thresholds are kept`() = runTest {
        viewModel.uiState.first { it.loaded }
        assertEquals(30, viewModel.cooldownFor(2890.0, "CZK", null))
        assertEquals(30, viewModel.cooldownFor(null, "CZK", null))
        assertEquals(12, viewModel.cooldownFor(2890.0, "CZK", 12))

        assertTrue(viewModel.setCooldowns(WantCooldowns.DEFAULT.copy(mediumUnder = 2000.0, largeDays = 60)))
        shadowOf(Looper.getMainLooper()).idle()

        viewModel.uiState.first { it.cooldowns.largeDays == 60 }
        assertEquals(60, viewModel.cooldownFor(2890.0, "CZK", null))
        assertNotNull(viewModel.add(WantDraft("Headphones", "Commute", price = 2890.0)))
        assertEquals(60, wants.all().single().cooldownDays)
    }

    @Test
    fun `the bar reads a want's price, picked wait and reason`() {
        val draft = viewModel.preview("Kindle 3290 Kč wait 2 weeks because I read on the train")

        assertEquals(WantDraft("Kindle", "I read on the train", price = 3290.0, currency = "CZK", pickedDays = 14), draft)
        assertEquals(14, viewModel.cooldownFor(draft.price, draft.currency, draft.pickedDays))
    }

    @Test
    fun `a line without a price takes the owner's currency, and its wait comes from the thresholds`() {
        val draft = viewModel.preview("Desk lamp because the desk is dark")

        assertEquals("CZK", draft.currency)
        assertEquals(null, draft.price)
        assertEquals(30, viewModel.cooldownFor(draft.price, draft.currency, draft.pickedDays))
    }

    @Test
    fun `a line with a title and a reason adds the want with the cooldown it picked`() = runTest {
        val outcome = viewModel.addLine("Kindle 3290 Kč wait 2 weeks because I read on the train")

        assertEquals(LineOutcome.Added, outcome)
        val want = wants.all().single()
        assertEquals("Kindle", want.title)
        assertEquals("I read on the train", want.reason)
        assertEquals(3290.0, want.price!!, 1e-9)
        assertEquals("CZK", want.currency)
        assertEquals(14, want.cooldownDays)
    }

    @Test
    fun `without a picked wait the price gives the cooldown`() = runTest {
        assertEquals(LineOutcome.Added, viewModel.addLine("Bike 12 990 Kč because the old one broke"))

        assertEquals(90, wants.all().single().cooldownDays)
    }

    @Test
    fun `a line without its reason opens the want form filled in, and adds nothing`() = runTest {
        val outcome = viewModel.addLine("Kindle 3290 Kč wait 10 days")

        assertEquals(LineOutcome.OpenForm(WantDraft("Kindle", "", price = 3290.0, currency = "CZK", pickedDays = 10)), outcome)
        assertTrue(wants.all().isEmpty())
    }

    @Test
    fun `a line with only a reason opens the form too`() = runTest {
        val outcome = viewModel.addLine("because I read a lot")

        assertEquals(LineOutcome.OpenForm(WantDraft("", "I read a lot", currency = "CZK")), outcome)
        assertTrue(wants.all().isEmpty())
    }

    private fun kotlinx.coroutines.CoroutineScope.launchCollect(model: WantsViewModel, into: MutableList<WantUndo>) =
        launch { model.undo.collect { into += it } }
}
