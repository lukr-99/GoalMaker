package com.goalmaker.app.ui.tally

/** One clock hour of the day chart on this phone: its seconds and each category's, most first. */
data class TallyHourBar(val hour: Int, val seconds: Int, val parts: List<TallyHourPart>)
