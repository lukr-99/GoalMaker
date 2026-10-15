package com.goalmaker.app.ui.pcsignin

/** Where scanning a PC's sign-in code stands (ADR 0020). */
enum class PcSignInStep {
    /** Nothing scanned yet: what to do, and the Scan button. */
    READY,

    /** Asking the server which PC the code belongs to, or sending the answer. */
    CHECKING,

    /** "Sign in on DESKTOP-1?" with Sign in and Don't sign in. */
    ASK,

    /** Approved: the PC signs itself in within a couple of seconds. */
    APPROVED,

    DECLINED,

    /** The scanned code is not a GoalMaker PC sign-in. */
    NOT_A_CODE,

    /** The code ran out, was answered already, or is unknown. */
    GONE,

    OFFLINE,

    FAILED,

    /** This phone can't scan: no Google Play services, or the scanner could not start. */
    NO_SCANNER,
}
