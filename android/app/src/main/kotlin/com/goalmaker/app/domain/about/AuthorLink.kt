package com.goalmaker.app.domain.about

/** One place to reach the author: which one it is, the address it opens and the handle a button shows. */
data class AuthorLink(
    val id: String,
    val url: String,
    val handle: String,
)
