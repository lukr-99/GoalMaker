package com.goalmaker.app.domain.account

/**
 * The text in the QR code on the PC's sign-in screen (ADR 0020, contracts/vectors/pc-sign-in.json):
 * the prefix and the scan secret, 43 URL-safe base64 characters. It never holds anything that signs in.
 * Beside it the PC shows a two-digit match number, which the phone must pick from three.
 */
object PcSignInCode {
    const val PREFIX = "goalmaker://sign-in/pc/"
    const val MATCH_CHOICES = 3

    /** A match number is two digits: 10 to 99. */
    fun isMatchNumber(value: Int): Boolean = value in 10..99

    /** The phone's choices are three different match numbers. */
    fun isMatchChoices(values: List<Int>): Boolean =
        values.size == MATCH_CHOICES && values.all(::isMatchNumber) && values.distinct().size == MATCH_CHOICES

    private val SECRET = Regex("^[A-Za-z0-9_-]{43}$")

    /** The text the PC puts in its QR code. */
    fun qrText(scanSecret: String): String = PREFIX + scanSecret

    /** The scan secret in a scanned text, or null when it is not a GoalMaker PC sign-in. */
    fun scanSecretOf(text: String): String? {
        val trimmed = text.trim()
        if (!trimmed.startsWith(PREFIX)) return null
        return trimmed.removePrefix(PREFIX).takeIf(SECRET::matches)
    }
}
