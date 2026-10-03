package com.goalmaker.app.domain.account

/** The 6-digit code Supabase Auth emails for sign-in (supabase/config.toml: otp_length). */
@JvmInline
value class SignInCode private constructor(val value: String) {
    companion object {
        const val LENGTH = 6

        // Six ASCII digits standing alone, optionally split once as 123 456 or 123-456: not part of a
        // longer run of digits or letters, a time like 10:30, a phone number's groups or a decimal.
        private val CODE = Regex("""(?<![\p{L}0-9:+.\-/])(?<![0-9][ \-])([0-9]{3})[ \-]?([0-9]{3})(?![\p{L}0-9:])(?![ \-][0-9])""")

        /** Accepts digits with optional spaces ("123 456"); returns null otherwise. */
        fun parse(text: String): SignInCode? =
            text.filterNot(Char::isWhitespace)
                .takeIf { it.length == LENGTH && it.all { char -> char in '0'..'9' } }
                ?.let(::SignInCode)

        /**
         * The code in text copied from elsewhere, like the email's notification or subject
         * (contracts/vectors/sign-in-code.json, 'find'): the one standalone six-digit run, or null when
         * there is none or two different ones, since a guess could sign in with the wrong code.
         */
        fun find(text: String): SignInCode? =
            CODE.findAll(text).map { it.groupValues[1] + it.groupValues[2] }.distinct().singleOrNull()?.let(::SignInCode)
    }
}
