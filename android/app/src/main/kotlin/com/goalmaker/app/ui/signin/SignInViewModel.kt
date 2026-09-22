package com.goalmaker.app.ui.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goalmaker.app.application.auth.AuthGateway
import com.goalmaker.app.application.auth.AuthResult
import com.goalmaker.app.application.auth.DevSignIn
import com.goalmaker.app.domain.account.EmailAddress
import com.goalmaker.app.domain.account.SignInCode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the two-step email code sign-in. Success is observed through [AuthGateway.session]. A dev
 * build against the local stack reads the code out of the stack's own mailbox through [devCode] and
 * fills it in, and offers the dev account, which is that in one press (docs/sign-in.md).
 *
 * [onSignedIn] runs when a code is accepted, which is the owner proving who they are, so the app
 * lock has nothing left to ask. It is not called when a stored session comes back.
 */
class SignInViewModel(
    private val auth: AuthGateway,
    private val devCode: (suspend (String) -> String?)? = null,
    private val onSignedIn: () -> Unit = {},
) : ViewModel() {

    private val state = MutableStateFlow(SignInUiState(hasDevSignIn = devCode != null))
    val uiState: StateFlow<SignInUiState> = state.asStateFlow()

    fun onEmailChange(value: String) = state.update { it.copy(email = value, error = null) }

    fun onCodeChange(value: String) {
        val digits = value.filter(Char::isDigit).take(SignInCode.LENGTH)
        state.update { it.copy(code = digits, error = null) }
        if (digits.length == SignInCode.LENGTH) verifyCode()
    }

    fun sendCode() {
        val email = EmailAddress.parse(state.value.email)
            ?: return state.update { it.copy(error = SignInError.INVALID_EMAIL) }
        run(onSuccess = { it.copy(step = SignInStep.CODE, code = "") }, then = ::fillCode) { auth.sendCode(email) }
    }

    /**
     * Reads the code out of the local stack's mailbox and fills it in, which signs in, since a full
     * code verifies itself. The mail takes a moment to land, so it asks a few times before giving up
     * and leaving the owner to type it.
     */
    fun fillCode() {
        val fetch = devCode ?: return
        val email = state.value.email.trim()
        viewModelScope.launch {
            repeat(ATTEMPTS) { attempt ->
                val current = state.value
                if (current.step != SignInStep.CODE || current.code.isNotEmpty()) return@launch
                if (attempt > 0) delay(WAIT)
                fetch(email)?.let { code ->
                    onCodeChange(code)
                    return@launch
                }
            }
        }
    }

    fun verifyCode() {
        val current = state.value
        if (current.busy) return
        val email = EmailAddress.parse(current.email)
            ?: return state.update { it.copy(step = SignInStep.EMAIL, error = SignInError.INVALID_EMAIL) }
        val code = SignInCode.parse(current.code)
            ?: return state.update { it.copy(error = SignInError.INVALID_CODE) }
        run(onSuccess = { it }, then = onSignedIn) { auth.verifyCode(email, code) }
    }

    fun useAnotherEmail() = state.update { SignInUiState(email = it.email, hasDevSignIn = devCode != null) }

    /**
     * The dev account in one press: its code goes to the stack's mailbox like any other, and is read
     * back from there. It is an account of its own with its own data, so trying things out never
     * touches the one the owner signs in as.
     */
    fun signInAsDev() {
        if (devCode == null || state.value.busy) return
        state.update { it.copy(email = DevSignIn.EMAIL, error = null) }
        sendCode()
    }

    private fun run(
        onSuccess: (SignInUiState) -> SignInUiState,
        then: () -> Unit = {},
        action: suspend () -> AuthResult,
    ) {
        state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = action()
            state.update { current ->
                val idle = current.copy(busy = false)
                when (result) {
                    AuthResult.Success -> onSuccess(idle)
                    AuthResult.WrongOrExpiredCode -> idle.copy(error = SignInError.WRONG_CODE, code = "")
                    AuthResult.TooManyRequests -> idle.copy(error = SignInError.TOO_MANY_REQUESTS)
                    AuthResult.Offline -> idle.copy(error = SignInError.OFFLINE)
                    is AuthResult.Failed -> idle.copy(error = SignInError.OTHER, errorDetail = result.detail)
                }
            }
            if (result == AuthResult.Success) then()
        }
    }

    private companion object {
        // The mail lands within a second or so on a local stack; after this the owner types it.
        const val WAIT = 600L
        const val ATTEMPTS = 6
    }
}
