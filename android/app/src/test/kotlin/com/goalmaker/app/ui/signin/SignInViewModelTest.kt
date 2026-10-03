package com.goalmaker.app.ui.signin

import com.goalmaker.app.application.auth.AuthGateway
import com.goalmaker.app.application.auth.AuthResult
import com.goalmaker.app.application.auth.AuthSession
import com.goalmaker.app.application.auth.DevSignIn
import com.goalmaker.app.domain.account.EmailAddress
import com.goalmaker.app.domain.account.SignInCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SignInViewModelTest {
    private class FakeAuth : AuthGateway {
        override val session: StateFlow<AuthSession> = MutableStateFlow(AuthSession.SignedOut)
        var sendResult: AuthResult = AuthResult.Success
        var verifyResult: AuthResult = AuthResult.Success
        val sent = mutableListOf<String>()
        val verified = mutableListOf<Pair<String, String>>()

        override suspend fun sendCode(email: EmailAddress): AuthResult {
            sent += email.value
            return sendResult
        }

        override suspend fun verifyCode(email: EmailAddress, code: SignInCode): AuthResult {
            verified += email.value to code.value
            return verifyResult
        }

        override suspend fun signOut() = Unit
    }

    private val auth = FakeAuth()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `an invalid email is caught before anything is sent`() {
        val viewModel = SignInViewModel(auth)
        viewModel.onEmailChange("nope")
        viewModel.sendCode()
        assertEquals(SignInError.INVALID_EMAIL, viewModel.uiState.value.error)
        assertEquals(emptyList<String>(), auth.sent)
    }

    @Test
    fun `sending a code moves to the code step`() {
        val viewModel = SignInViewModel(auth)
        viewModel.onEmailChange(" me@example.com ")
        viewModel.sendCode()
        assertEquals(listOf("me@example.com"), auth.sent)
        assertEquals(SignInStep.CODE, viewModel.uiState.value.step)
        assertFalse(viewModel.uiState.value.busy)
    }

    @Test
    fun `the sixth digit submits the code`() {
        val viewModel = SignInViewModel(auth)
        viewModel.onEmailChange("me@example.com")
        viewModel.sendCode()
        viewModel.onCodeChange("123 45")
        assertEquals(emptyList<Pair<String, String>>(), auth.verified)
        viewModel.onCodeChange("1234567")
        assertEquals(listOf("me@example.com" to "123456"), auth.verified)
    }

    @Test
    fun `a code copied from the email's notification goes in by itself, once`() {
        val viewModel = SignInViewModel(auth)
        viewModel.onEmailChange("me@example.com")
        assertFalse(viewModel.takeCopiedCode("482913 is your GoalMaker code"))

        viewModel.sendCode()
        assertFalse(viewModel.takeCopiedCode("Sent 2026-10-03 at 10:30"))
        assertTrue(viewModel.takeCopiedCode("482913 is your GoalMaker code"))
        assertEquals(listOf("me@example.com" to "482913"), auth.verified)
    }

    @Test
    fun `paste takes the code, or says nothing was copied`() {
        auth.verifyResult = AuthResult.WrongOrExpiredCode
        val viewModel = SignInViewModel(auth)
        viewModel.onEmailChange("me@example.com")
        viewModel.sendCode()

        viewModel.pasteCode("hello")
        assertEquals(SignInError.NO_CODE_COPIED, viewModel.uiState.value.error)
        viewModel.pasteCode("Your sign-in code is 482 913.")
        assertEquals(listOf("me@example.com" to "482913"), auth.verified)
    }

    @Test
    fun `a wrong code clears the field and explains`() {
        auth.verifyResult = AuthResult.WrongOrExpiredCode
        val viewModel = SignInViewModel(auth)
        viewModel.onEmailChange("me@example.com")
        viewModel.sendCode()
        viewModel.onCodeChange("000000")
        assertEquals(SignInError.WRONG_CODE, viewModel.uiState.value.error)
        assertEquals("", viewModel.uiState.value.code)
    }

    @Test
    fun `a code that is accepted says so, and a wrong one does not`() {
        // The app lock listens for this: the owner has just proved who they are (docs/sign-in.md).
        var signedIn = 0
        val viewModel = SignInViewModel(auth, onSignedIn = { signedIn++ })
        viewModel.onEmailChange("me@example.com")

        auth.verifyResult = AuthResult.WrongOrExpiredCode
        viewModel.onCodeChange("111111")
        assertEquals(0, signedIn)

        auth.verifyResult = AuthResult.Success
        viewModel.onCodeChange("123456")
        assertEquals(1, signedIn)
    }

    @Test
    fun `one press signs in as the dev account`() {
        val viewModel = SignInViewModel(auth, devCode = { email -> "112233".takeIf { email == DevSignIn.EMAIL } })
        assertTrue(viewModel.uiState.value.hasDevSignIn)

        viewModel.signInAsDev()

        assertEquals(DevSignIn.EMAIL, viewModel.uiState.value.email)
        assertEquals(listOf(DevSignIn.EMAIL), auth.sent)
        assertEquals(listOf(DevSignIn.EMAIL to "112233"), auth.verified)
    }

    @Test
    fun `a dev build fills the code it reads in the stack's mailbox`() {
        val viewModel = SignInViewModel(auth, devCode = { email -> "654321".takeIf { email == "me@example.com" } })
        viewModel.onEmailChange("me@example.com")
        viewModel.sendCode()
        assertEquals(listOf("me@example.com" to "654321"), auth.verified)
    }

    @Test
    fun `a release build has neither door`() {
        assertFalse(SignInViewModel(auth).uiState.value.hasDevSignIn)
    }

    @Test
    fun `offline is reported plainly`() {
        auth.sendResult = AuthResult.Offline
        val viewModel = SignInViewModel(auth)
        viewModel.onEmailChange("me@example.com")
        viewModel.sendCode()
        assertEquals(SignInError.OFFLINE, viewModel.uiState.value.error)
        assertEquals(SignInStep.EMAIL, viewModel.uiState.value.step)
    }

    @Test
    fun `a session that ended says so, and keeps saying it on another email`() {
        val viewModel = SignInViewModel(auth, sessionEnded = { true })
        assertFalse(viewModel.uiState.value.sessionEnded)

        viewModel.checkSession()
        assertTrue(viewModel.uiState.value.sessionEnded)

        viewModel.onEmailChange("me@example.com")
        viewModel.sendCode()
        viewModel.useAnotherEmail()
        assertTrue(viewModel.uiState.value.sessionEnded)
    }

    @Test
    fun `a first sign-in says nothing about a session`() {
        val viewModel = SignInViewModel(auth)
        viewModel.checkSession()
        assertFalse(viewModel.uiState.value.sessionEnded)
    }
}
