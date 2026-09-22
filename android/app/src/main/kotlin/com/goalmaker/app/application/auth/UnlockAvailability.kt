package com.goalmaker.app.application.auth

/** Whether this phone can prove who is holding it, and why not when it cannot (docs/sign-in.md). */
enum class UnlockAvailability {
    /** A fingerprint, a face or the screen lock can be asked for. */
    READY,

    /** The phone can ask, but there is no fingerprint, face or screen lock set up to ask for. */
    NOTHING_ENROLLED,

    /** No hardware to ask with, or an Android too old for the prompt this app puts up. */
    UNAVAILABLE,
}
