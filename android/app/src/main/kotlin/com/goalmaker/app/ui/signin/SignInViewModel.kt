package com.goalmaker.app.ui.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goalmaker.app.application.auth.AuthGateway
import com.goalmaker.app.application.auth.AuthResult
import com.goalmaker.app.domain.account.EmailAddress
import com.goalmaker.app.domain.account.SignInCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Drives the two-step email code sign-in. Success is observed through [AuthGateway.session]. */
class SignInViewModel(private val auth: AuthGateway) : ViewModel() {

    private val state = MutableStateFlow(SignInUiState())
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
        run(onSuccess = { it.copy(step = SignInStep.CODE, code = "") }) { auth.sendCode(email) }
    }

    fun verifyCode() {
        val current = state.value
        if (current.busy) return
        val email = EmailAddress.parse(current.email)
            ?: return state.update { it.copy(step = SignInStep.EMAIL, error = SignInError.INVALID_EMAIL) }
        val code = SignInCode.parse(current.code)
            ?: return state.update { it.copy(error = SignInError.INVALID_CODE) }
        run(onSuccess = { it }) { auth.verifyCode(email, code) }
    }

    fun useAnotherEmail() = state.update { SignInUiState(email = it.email) }

    private fun run(onSuccess: (SignInUiState) -> SignInUiState, action: suspend () -> AuthResult) {
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
        }
    }
}
