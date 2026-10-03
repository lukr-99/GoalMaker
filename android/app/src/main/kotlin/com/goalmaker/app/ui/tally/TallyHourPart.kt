package com.goalmaker.app.ui.tally

/** One category's seconds in an hour of the day chart, with its name (blank once removed) and palette color. */
data class TallyHourPart(val category: String, val name: String, val color: String, val seconds: Int)
