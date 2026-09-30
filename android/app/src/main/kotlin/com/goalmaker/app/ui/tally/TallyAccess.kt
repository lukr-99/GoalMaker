package com.goalmaker.app.ui.settings

/** What the Tally section shows: whether Tally is on, and whether the owner has granted usage access. */
data class TallyUiState(val on: Boolean, val granted: Boolean) {
    /** On without access: the card says what is read and what syncs, and opens the system's page. */
    val askForAccess: Boolean get() = on && !granted
}
