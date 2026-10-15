package com.goalmaker.app.ui.pcsignin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goalmaker.app.application.auth.PcSignInAnswer
import com.goalmaker.app.application.auth.PcSignInApprovals
import com.goalmaker.app.application.sync.RemoteRejectedException
import com.goalmaker.app.application.sync.RemoteUnavailableException
import com.goalmaker.app.domain.account.PcSignInCode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Signing in on a PC (ADR 0020): the owner scans the QR code on the PC's sign-in screen, the screen
 * says which PC it is, and the owner picks the number the PC shows, or declines. A wrong number burns
 * the code on the server. Nothing is sent before the owner taps, and the scan secret is kept only in
 * this view model.
 */
class PcSignInViewModel(
    private val approvals: PcSignInApprovals,
    private val io: CoroutineDispatcher,
) : ViewModel() {
    private val state = MutableStateFlow(PcSignInUiState())
    val uiState: StateFlow<PcSignInUiState> = state.asStateFlow()

    private var scanSecret: String? = null

    /** The scanner read [text]. */
    fun scanned(text: String) {
        val secret = PcSignInCode.scanSecretOf(text)
        if (secret == null) {
            scanSecret = null
            state.value = PcSignInUiState(PcSignInStep.NOT_A_CODE)
            return
        }
        scanSecret = secret
        call {
            val request = withContext(io) { approvals.look(secret) }
            state.value = when {
                request == null -> PcSignInUiState(PcSignInStep.GONE)
                // Without three numbers to pick from there is nothing safe to ask.
                !PcSignInCode.isMatchChoices(request.choices) -> PcSignInUiState(PcSignInStep.FAILED, request.device)
                else -> PcSignInUiState(PcSignInStep.ASK, request.device, request.choices)
            }
        }
    }

    /** The owner picked [number] as the one the PC shows. */
    fun pick(number: Int) = answer(approve = true, picked = number)

    fun decline() = answer(approve = false, picked = null)

    /** The scanner could not start on this phone. */
    fun scannerUnavailable() {
        scanSecret = null
        state.value = PcSignInUiState(PcSignInStep.NO_SCANNER)
    }

    /** Back to the start, to scan another code. */
    fun again() {
        scanSecret = null
        state.value = PcSignInUiState()
    }

    private fun answer(approve: Boolean, picked: Int?) {
        val secret = scanSecret ?: return
        if (state.value.step != PcSignInStep.ASK) return
        if (approve && picked !in state.value.choices) return
        val device = state.value.device
        call {
            val answer = withContext(io) { approvals.answer(secret, approve, picked) }
            scanSecret = null
            val step = when (answer) {
                PcSignInAnswer.APPROVED -> PcSignInStep.APPROVED
                PcSignInAnswer.DECLINED -> PcSignInStep.DECLINED
                PcSignInAnswer.MISMATCH -> PcSignInStep.MISMATCH
                PcSignInAnswer.GONE -> PcSignInStep.GONE
                PcSignInAnswer.NO_EMAIL -> PcSignInStep.FAILED
            }
            state.value = PcSignInUiState(step, device)
        }
    }

    private fun call(work: suspend () -> Unit) {
        val device = state.value.device
        state.value = PcSignInUiState(PcSignInStep.CHECKING, device)
        viewModelScope.launch {
            try {
                work()
            } catch (_: RemoteUnavailableException) {
                state.update { PcSignInUiState(PcSignInStep.OFFLINE, device) }
            } catch (_: RemoteRejectedException) {
                state.update { PcSignInUiState(PcSignInStep.FAILED, device) }
            }
        }
    }
}
