package com.goalmaker.app.domain.planning

/**
 * One prompt from the library (contracts/content/prompts.json): its [id] as reviews store it, the
 * [category] it rotates in, the review kinds it suits, its [text] with `{period}` and `{subject}`
 * still in it, and the [trigger] that makes it worth asking, if any.
 */
data class ReviewPrompt(
    val id: String,
    val category: String,
    val reviews: Set<String>,
    val text: String,
    val trigger: String? = null,
)
