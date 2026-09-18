package com.goalmaker.app.domain.account

/** The 6-digit code Supabase Auth emails for sign-in (supabase/config.toml: otp_length). */
@JvmInline
value class SignInCode private constructor(val value: String) {
    companion object {
        const val LENGTH = 6

        /** Accepts digits with optional spaces ("123 456"); returns null otherwise. */
        fun parse(text: String): SignInCode? =
            text.filterNot(Char::isWhitespace)
                .takeIf { it.length == LENGTH && it.all { char -> char in '0'..'9' } }
                ?.let(::SignInCode)
    }
}
