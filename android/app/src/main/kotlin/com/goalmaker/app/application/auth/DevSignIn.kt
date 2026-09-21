package com.goalmaker.app.application.auth

import java.net.URI

/**
 * How a dev build gets past the sign-in screen without a mailbox in the way (docs/sign-in.md,
 * contracts/vectors/dev-sign-in.json). Only the local Supabase stack has this door: it catches every
 * message it sends in a mailbox on 55324, beside its API on 55321, where a build may read the code and
 * fill it in. Anything else, the cloud project above all, has no mailbox and no dev account.
 */
object DevSignIn {
    /** The account a dev build offers, so trying things out never costs the owner's own. */
    const val EMAIL = "dev@goalmaker.test"

    /** The port the local stack's API listens on (supabase/config.toml). */
    const val API_PORT = 55321

    /** The port its mailbox listens on. */
    const val MAIL_PORT = 55324

    private val code = Regex("""(?<!\d)\d{6}(?!\d)""")

    /** The mailbox behind a backend address, or null when it isn't the local stack. */
    fun mailboxOf(backend: String?): String? {
        val url = runCatching { URI(backend ?: return null) }.getOrNull() ?: return null
        if (url.scheme != "http" || url.port != API_PORT || url.host.isNullOrEmpty()) return null
        return "http://${url.host}:$MAIL_PORT"
    }

    /** The sign-in code in a message: the first run of exactly six digits. */
    fun codeIn(message: String?): String? = message?.let { code.find(it)?.value }
}
