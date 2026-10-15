package com.goalmaker.app.ui.pcsignin

/** The scan and approve screen: its [step], and the PC's name once the server said which PC asked. */
data class PcSignInUiState(
    val step: PcSignInStep = PcSignInStep.READY,
    val device: String? = null,
)
