package com.goalmaker.app.ui.pcsignin

/**
 * The scan and approve screen: its [step], the PC's name once the server said which PC asked, and the
 * numbers the owner picks the PC's match number from.
 */
data class PcSignInUiState(
    val step: PcSignInStep = PcSignInStep.READY,
    val device: String? = null,
    val choices: List<Int> = emptyList(),
)
