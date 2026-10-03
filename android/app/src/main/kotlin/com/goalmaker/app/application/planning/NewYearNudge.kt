package com.goalmaker.app.application.planning

/**
 * The January nudge (spec story 66, docs/reviews.md): the [year] that has begun, and what is still to
 * do for it: look back on the year before ([review]) and set this year's goals ([goals]).
 */
data class NewYearNudge(val year: Int, val review: Boolean, val goals: Boolean)
