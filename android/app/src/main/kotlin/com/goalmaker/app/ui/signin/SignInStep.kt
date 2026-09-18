package com.goalmaker.app.ui.signin

/** Sign-in happens in two steps: email, then the emailed code. */
enum class SignInStep {
    EMAIL,
    CODE,
}
