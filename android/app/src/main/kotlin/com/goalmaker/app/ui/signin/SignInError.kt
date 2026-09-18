package com.goalmaker.app.ui.signin

/** Why the last sign-in action didn't work; the screen turns it into a sentence. */
enum class SignInError {
    INVALID_EMAIL,
    INVALID_CODE,
    WRONG_CODE,
    TOO_MANY_REQUESTS,
    OFFLINE,
    OTHER,
}
