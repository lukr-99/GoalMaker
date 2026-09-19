package com.goalmaker.app.domain.planning

/** A prompt as a review asks it: the prompt's id, its text with everything filled in, and what it is about. */
data class ReviewQuestion(val promptId: String, val text: String, val subject: String? = null)
