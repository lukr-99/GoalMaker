package com.goalmaker.app.data.auth

import com.goalmaker.app.application.auth.AuthGateway
import com.goalmaker.app.application.auth.AuthResult
import com.goalmaker.app.application.auth.AuthSession
import com.goalmaker.app.domain.account.EmailAddress
import com.goalmaker.app.domain.account.SignInCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A dev build's [AuthGateway]: always signed in as one fixed owner who lives only on this phone, so
 * trying a change out never waits on a sign-in (docs/sign-in.md). Sign-in is for release builds.
 */
class LocalOnlyAuthGateway : AuthGateway {
    override val session: StateFlow<AuthSession> = MutableStateFlow(AuthSession.SignedIn(OWNER_ID, ""))

    override suspend fun sendCode(email: EmailAddress): AuthResult = AuthResult.Success

    override suspend fun verifyCode(email: EmailAddress, code: SignInCode): AuthResult = AuthResult.Success

    override suspend fun signOut() = Unit

    companion object {
        /** The owner of every row a dev build writes. */
        const val OWNER_ID = "00000000-0000-4000-8000-00000000d0e0"
    }
}
