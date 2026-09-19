package com.goalmaker.app.application.planning

/** One prompt a review asked and the answer written to it (docs/reviews.md). */
data class Reflection(val promptId: String, val answer: String)
