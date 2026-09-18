package com.goalmaker.app.domain.account

/** A trimmed, plausibly valid email address. Supabase Auth does the real check. */
@JvmInline
value class EmailAddress private constructor(val value: String) {
    companion object {
        private val pattern = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        fun parse(text: String): EmailAddress? =
            text.trim().takeIf { it.length <= 254 && pattern.matches(it) }?.let(::EmailAddress)
    }
}
