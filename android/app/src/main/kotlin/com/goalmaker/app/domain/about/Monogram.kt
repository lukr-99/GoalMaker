package com.goalmaker.app.domain.about

import java.util.Locale

/**
 * The letters on the author card's badge: the first letter of the first and the last word of a name,
 * upper case (contracts/vectors/about.json). One word gives one letter; a blank name gives none.
 */
object Monogram {
    fun of(name: String): String {
        val words = name.trim().split(WHITESPACE).filter { it.isNotEmpty() }
        if (words.isEmpty()) return ""
        val picked = if (words.size == 1) words else listOf(words.first(), words.last())
        return picked.joinToString("") { it.substring(0, it.offsetByCodePoints(0, 1)) }.uppercase(Locale.ROOT)
    }

    private val WHITESPACE = Regex("""\s+""")
}
