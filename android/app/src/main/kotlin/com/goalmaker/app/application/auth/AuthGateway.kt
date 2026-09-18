package com.goalmaker.app.application.auth

import com.goalmaker.app.domain.account.EmailAddress
import com.goalmaker.app.domain.account.SignInCode
import kotlinx.coroutines.flow.StateFlow

/** Sign-in with an emailed 6-digit code. The Supabase adapter implements it; tests use a fake. */
interface AuthGateway {
    val session: StateFlow<AuthSession>

    suspend fun sendCode(email: EmailAddress): AuthResult

    suspend fun verifyCode(email: EmailAddress, code: SignInCode): AuthResult

    suspend fun signOut()
}
