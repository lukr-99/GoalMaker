package com.goalmaker.app.ui.pcsignin

import com.goalmaker.app.application.auth.PcSignInAnswer
import com.goalmaker.app.application.auth.PcSignInApprovals
import com.goalmaker.app.application.auth.PcSignInRequest
import com.goalmaker.app.application.sync.RemoteUnavailableException
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Scanning a PC's sign-in code and answering it over a fake server (ADR 0020). */
@OptIn(ExperimentalCoroutinesApi::class)
class PcSignInViewModelTest {
    private class FakeApprovals : PcSignInApprovals {
        var request: PcSignInRequest? =
            PcSignInRequest("DESKTOP-1", Instant.EPOCH, Instant.EPOCH.plusSeconds(120), listOf(17, 42, 88))
        var match = 42
        var gone = false
        var offline = false
        val looked = mutableListOf<String>()
        val answers = mutableListOf<Triple<String, Boolean, Int?>>()

        override suspend fun look(scanSecret: String): PcSignInRequest? {
            if (offline) throw RemoteUnavailableException("offline")
            looked += scanSecret
            return request
        }

        override suspend fun answer(scanSecret: String, approve: Boolean, picked: Int?): PcSignInAnswer {
            if (offline) throw RemoteUnavailableException("offline")
            answers += Triple(scanSecret, approve, picked)
            return when {
                gone -> PcSignInAnswer.GONE
                !approve -> PcSignInAnswer.DECLINED
                picked != match -> PcSignInAnswer.MISMATCH
                else -> PcSignInAnswer.APPROVED
            }
        }
    }

    private val approvals = FakeApprovals()
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = PcSignInViewModel(approvals, dispatcher)

    @Test
    fun `a scanned code asks which PC it is and sends nothing else until the owner answers`() {
        val viewModel = viewModel()

        viewModel.scanned("goalmaker://sign-in/pc/$SECRET\n")

        assertEquals(PcSignInUiState(PcSignInStep.ASK, "DESKTOP-1", listOf(17, 42, 88)), viewModel.uiState.value)
        assertEquals(listOf(SECRET), approvals.looked)
        assertTrue(approvals.answers.isEmpty())
    }

    @Test
    fun `picking the PC's number signs it in as this owner`() {
        val viewModel = viewModel()
        viewModel.scanned(CODE)

        viewModel.pick(42)

        assertEquals(PcSignInUiState(PcSignInStep.APPROVED, "DESKTOP-1"), viewModel.uiState.value)
        assertEquals(listOf(Triple(SECRET, true, 42)), approvals.answers)
    }

    @Test
    fun `picking another number says the code no longer works`() {
        val viewModel = viewModel()
        viewModel.scanned(CODE)

        viewModel.pick(17)

        assertEquals(PcSignInStep.MISMATCH, viewModel.uiState.value.step)
        assertEquals(listOf(Triple(SECRET, true, 17)), approvals.answers)
    }

    @Test
    fun `a number that isn't one of the choices is never sent`() {
        val viewModel = viewModel()
        viewModel.scanned(CODE)

        viewModel.pick(50)

        assertEquals(PcSignInStep.ASK, viewModel.uiState.value.step)
        assertTrue(approvals.answers.isEmpty())
    }

    @Test
    fun `without three numbers to pick from nothing is asked`() {
        approvals.request = approvals.request!!.copy(choices = listOf(42, 42, 88))
        val viewModel = viewModel()

        viewModel.scanned(CODE)

        assertEquals(PcSignInStep.FAILED, viewModel.uiState.value.step)
    }

    @Test
    fun `declining says the PC stays signed out`() {
        val viewModel = viewModel()
        viewModel.scanned(CODE)

        viewModel.decline()

        assertEquals(PcSignInUiState(PcSignInStep.DECLINED, "DESKTOP-1"), viewModel.uiState.value)
        assertEquals(listOf(Triple(SECRET, false, null)), approvals.answers)
    }

    @Test
    fun `an answer is sent once`() {
        val viewModel = viewModel()
        viewModel.scanned(CODE)
        viewModel.pick(42)

        viewModel.pick(42)
        viewModel.decline()

        assertEquals(1, approvals.answers.size)
    }

    @Test
    fun `a code that ran out before the answer says so`() {
        approvals.gone = true
        val viewModel = viewModel()
        viewModel.scanned(CODE)

        viewModel.pick(42)

        assertEquals(PcSignInStep.GONE, viewModel.uiState.value.step)
    }

    @Test
    fun `a code the server does not know is gone`() {
        approvals.request = null
        val viewModel = viewModel()

        viewModel.scanned(CODE)

        assertEquals(PcSignInUiState(PcSignInStep.GONE), viewModel.uiState.value)
    }

    @Test
    fun `something else scanned never reaches the server`() {
        val viewModel = viewModel()

        viewModel.scanned("https://example.com/$SECRET")

        assertEquals(PcSignInUiState(PcSignInStep.NOT_A_CODE), viewModel.uiState.value)
        assertTrue(approvals.looked.isEmpty())
    }

    @Test
    fun `offline is said plainly`() {
        approvals.offline = true
        val viewModel = viewModel()

        viewModel.scanned(CODE)

        assertEquals(PcSignInStep.OFFLINE, viewModel.uiState.value.step)
    }

    @Test
    fun `a phone that cannot scan is told to use the emailed code, and again starts over`() {
        val viewModel = viewModel()

        viewModel.scannerUnavailable()
        assertEquals(PcSignInStep.NO_SCANNER, viewModel.uiState.value.step)

        viewModel.again()
        assertEquals(PcSignInUiState(), viewModel.uiState.value)
    }

    private companion object {
        const val SECRET = "Qx3mT9vB2kLw8RfN1pZc7YhJ4sDg6EaU0oKiVt5nM-_"
        const val CODE = "goalmaker://sign-in/pc/$SECRET"
    }
}
