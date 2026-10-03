package com.goalmaker.app.ui.tally

import com.goalmaker.app.application.planning.TallyWindowTime

/**
 * One app under a category in the Tally place: its package (what a rule matches), the [name] it shows
 * under its icon (the package when the phone doesn't know it), its minutes, and on the PC its sites.
 */
data class TallyAppRow(val app: String, val name: String, val minutes: Int, val windows: List<TallyWindowTime> = emptyList())
