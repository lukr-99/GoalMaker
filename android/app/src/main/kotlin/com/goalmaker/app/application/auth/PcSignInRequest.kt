package com.goalmaker.app.application.auth

import java.time.Instant

/**
 * A PC asking to sign in. [device] is the name the PC gave: only a label, anyone can send any name.
 * [choices] are the numbers the owner picks the PC's match number from (ADR 0020).
 */
data class PcSignInRequest(val device: String, val createdAt: Instant, val expiresAt: Instant, val choices: List<Int>)
