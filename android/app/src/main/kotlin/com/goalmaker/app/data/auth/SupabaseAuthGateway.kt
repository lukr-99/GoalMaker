package com.goalmaker.app.data.auth

import com.goalmaker.app.application.auth.AuthGateway
import com.goalmaker.app.application.auth.AuthResult
import com.goalmaker.app.application.auth.AuthSession
import com.goalmaker.app.domain.account.EmailAddress
import com.goalmaker.app.domain.account.SignInCode
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** [AuthGateway] over Supabase Auth's email one-time codes. */
class SupabaseAuthGateway(
    private val client: SupabaseClient,
    scope: CoroutineScope,
) : AuthGateway {

    override val session: StateFlow<AuthSession> = client.auth.sessionStatus
        .map(::toSession)
        .stateIn(scope, SharingStarted.Eagerly, AuthSession.Loading)

    override suspend fun sendCode(email: EmailAddress): AuthResult = attempt {
        client.auth.signInWith(OTP) {
            this.email = email.value
            createUser = true
        }
    }

    override suspend fun verifyCode(email: EmailAddress, code: SignInCode): AuthResult = attempt {
        client.auth.verifyEmailOtp(type = OtpType.Email.EMAIL, email = email.value, token = code.value)
    }

    override suspend fun signOut() {
        try {
            client.auth.signOut()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Offline: the local session is cleared anyway; the server token expires on its own.
            client.auth.clearSession()
        }
    }

    private fun toSession(status: SessionStatus): AuthSession = when (status) {
        is SessionStatus.Initializing -> AuthSession.Loading
        is SessionStatus.Authenticated -> AuthSession.SignedIn(status.session.user?.email.orEmpty())
        // A failed refresh (usually offline) keeps the stored session; the user is still signed in.
        is SessionStatus.RefreshFailure ->
            AuthSession.SignedIn(client.auth.currentSessionOrNull()?.user?.email.orEmpty())
        is SessionStatus.NotAuthenticated -> AuthSession.SignedOut
    }

    private suspend fun attempt(block: suspend () -> Unit): AuthResult = try {
        block()
        AuthResult.Success
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: AuthRestException) {
        val code = error.error.lowercase()
        when {
            "expired" in code || "invalid" in code || "otp" in code -> AuthResult.WrongOrExpiredCode
            "rate" in code || error.statusCode == TOO_MANY_REQUESTS -> AuthResult.TooManyRequests
            else -> AuthResult.Failed(error.description ?: error.error)
        }
    } catch (error: RestException) {
        if (error.statusCode == TOO_MANY_REQUESTS) AuthResult.TooManyRequests else AuthResult.Failed(error.error)
    } catch (_: HttpRequestException) {
        AuthResult.Offline
    }

    private companion object {
        const val TOO_MANY_REQUESTS = 429
    }
}
