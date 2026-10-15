package com.goalmaker.app.application.auth

import java.time.Instant

/** A PC asking to sign in. [device] is the name the PC gave: only a label, anyone can send any name. */
data class PcSignInRequest(val device: String, val createdAt: Instant, val expiresAt: Instant)
