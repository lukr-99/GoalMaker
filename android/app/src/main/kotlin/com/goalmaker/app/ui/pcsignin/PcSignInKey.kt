package com.goalmaker.app.ui.pcsignin

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Signing in on a PC by scanning its QR code (ADR 0020). */
@Serializable
data object PcSignInKey : NavKey
