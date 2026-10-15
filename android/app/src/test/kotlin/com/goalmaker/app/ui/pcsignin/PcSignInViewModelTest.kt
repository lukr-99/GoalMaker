package com.goalmaker.app.ui.pcsignin

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
        var request: PcSignInRequest? = PcSignInRequest("DESKTOP-1", Instant.EPOCH, Instant.EPOCH.plusSeconds(120))
        var answered = true
        var offline = false
        val looked = mutableListOf<String>()
        val answers = mutableListOf<Pair<String, Boolean>>()

        override suspend fun look(scanSecret: String): PcSignInRequest? {
            if (offline) throw RemoteUnavailableException("offline")
            looked += scanSecret
            return request
        }

        override suspend fun answer(scanSecret: String, approve: Boolean): Boolean {
            if (offline) throw RemoteUnavailableException("offline")
            answers += scanSecret to approve
            return answered
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

        assertEquals(PcSignInUiState(PcSignInStep.ASK, "DESKTOP-1"), viewModel.uiState.value)
        assertEquals(listOf(SECRET), approvals.looked)
        assertTrue(approvals.answers.isEmpty())
    }

    @Test
    fun `approving signs the PC in as this owner`() {
        val viewModel = viewModel()
        viewModel.scanned(CODE)

        viewModel.approve()

        assertEquals(PcSignInUiState(PcSignInStep.APPROVED, "DESKTOP-1"), viewModel.uiState.value)
        assertEquals(listOf(SECRET to true), approvals.answers)
    }

    @Test
    fun `declining says the PC stays signed out`() {
        val viewModel = viewModel()
        viewModel.scanned(CODE)

        viewModel.decline()

        assertEquals(PcSignInUiState(PcSignInStep.DECLINED, "DESKTOP-1"), viewModel.uiState.value)
        assertEquals(listOf(SECRET to false), approvals.answers)
    }

    @Test
    fun `an answer is sent once`() {
        val viewModel = viewModel()
        viewModel.scanned(CODE)
        viewModel.approve()

        viewModel.approve()
        viewModel.decline()

        assertEquals(1, approvals.answers.size)
    }

    @Test
    fun `a code that ran out before the answer says so`() {
        approvals.answered = false
        val viewModel = viewModel()
        viewModel.scanned(CODE)

        viewModel.approve()

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
