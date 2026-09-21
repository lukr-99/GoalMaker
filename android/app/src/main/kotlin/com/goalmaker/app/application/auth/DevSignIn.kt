package com.goalmaker.app.application.auth

import java.net.URI

/**
 * How a dev build gets past the sign-in screen without a mailbox in the way (docs/sign-in.md,
 * contracts/vectors/dev-sign-in.json). Only the local Supabase stack has these doors: it takes [CODE]
 * for [EMAIL] without sending anything, and it keeps the mail for every other address where a build
 * may read it, on 55324 beside its API on 55321, both over plain http. Anything else, the cloud
 * project above all, has neither.
 */
object DevSignIn {
    /** The address the local stack lets in with a fixed code (supabase/config.toml). */
    const val EMAIL = "dev@goalmaker.test"

    /** The code it takes for that address, which no mail ever carries. */
    const val CODE = "424242"

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
