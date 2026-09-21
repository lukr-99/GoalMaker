package com.goalmaker.app.ui.signin

/** Everything the sign-in screen shows. */
data class SignInUiState(
    val step: SignInStep = SignInStep.EMAIL,
    val email: String = "",
    val code: String = "",
    val busy: Boolean = false,
    val error: SignInError? = null,
    val errorDetail: String = "",
    /** A dev build on the local stack can fetch the code itself, so it offers to. */
    val hasDevCode: Boolean = false,
)
