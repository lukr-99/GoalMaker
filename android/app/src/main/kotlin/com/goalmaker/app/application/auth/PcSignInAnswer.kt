package com.goalmaker.app.application.auth

/** What the server made of the phone's answer to a PC sign-in (supabase/migrations/0028). */
enum class PcSignInAnswer {
    APPROVED,
    DECLINED,

    /** The number picked was not the PC's, so the request is burned. */
    MISMATCH,

    /** The code ran out, was answered already, or is unknown. */
    GONE,

    /** This account has no address Supabase Auth could make a code for. */
    NO_EMAIL,
}
