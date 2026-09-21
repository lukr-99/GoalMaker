package com.goalmaker.app.application.auth

import java.net.URI

/**
 * Where a dev build finds the sign-in code it was just sent (docs/sign-in.md,
 * contracts/vectors/dev-mailbox.json). Only the local Supabase stack keeps its mail where a build may
 * read it: it serves its API on 55321 and the mailbox that caught the mail on 55324, both over plain
 * http. Anything else, the cloud project above all, has no mailbox here.
 */
object DevMailbox {
    /** The port the local stack's API listens on (supabase/config.toml). */
    const val API_PORT = 55321

    /** The port its mailbox listens on. */
    const val MAIL_PORT = 55324

    private val code = Regex("""(?<!\d)\d{6}(?!\d)""")

    /** The mailbox behind a backend address, or null when it isn't the local stack. */
    fun of(backend: String?): String? {
        val url = runCatching { URI(backend ?: return null) }.getOrNull() ?: return null
        if (url.scheme != "http" || url.port != API_PORT || url.host.isNullOrEmpty()) return null
        return "http://${url.host}:$MAIL_PORT"
    }

    /** The sign-in code in a message: the first run of exactly six digits. */
    fun codeIn(message: String?): String? = message?.let { code.find(it)?.value }
}
