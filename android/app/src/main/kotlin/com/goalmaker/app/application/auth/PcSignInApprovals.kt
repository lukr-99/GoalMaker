package com.goalmaker.app.application.auth

/**
 * The phone's half of the PC's QR sign-in (ADR 0020, supabase/migrations/0028): which PC a scanned
 * code belongs to, and the owner's answer, sent with the phone's own session. Every call throws
 * RemoteUnavailableException when the server can't be reached.
 */
interface PcSignInApprovals {
    /** The PC asking, or null when the code is unknown, ran out or was answered already. */
    suspend fun look(scanSecret: String): PcSignInRequest?

    /** Approves as the signed-in owner with the number the owner [picked], or declines. */
    suspend fun answer(scanSecret: String, approve: Boolean, picked: Int? = null): PcSignInAnswer
}
