package com.goalmaker.app.ui.tally

/** Tally's switch at the top of the place: whether Tally is on, and whether the owner has granted usage access. */
data class TallyAccess(val on: Boolean, val granted: Boolean) {
    /** On without access: the card says what is read and what syncs, and opens the system's page. */
    val askForAccess: Boolean get() = on && !granted
}
