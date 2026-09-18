package com.goalmaker.app.data.replica

import java.util.Locale

/**
 * Splits a migration script into statements, because androidx.sqlite runs one statement per call.
 * Follows SQLite's own sqlite3_complete() rules: semicolons inside comments, quotes and trigger
 * bodies (CREATE TRIGGER ... BEGIN ...; END;) don't end a statement.
 */
object SqlScript {
    private const val SEMI = 0
    private const val WS = 1
    private const val OTHER = 2
    private const val EXPLAIN = 3
    private const val CREATE = 4
    private const val TEMP = 5
    private const val TRIGGER = 6
    private const val END = 7

    private const val START = 1

    // sqlite3_complete()'s transition table: rows are states, columns the tokens above.
    private val transitions = arrayOf(
        intArrayOf(1, 0, 2, 3, 4, 2, 2, 2), // 0 invalid
        intArrayOf(1, 1, 2, 3, 4, 2, 2, 2), // 1 start
        intArrayOf(1, 2, 2, 2, 2, 2, 2, 2), // 2 normal
        intArrayOf(1, 3, 3, 2, 4, 2, 2, 2), // 3 explain
        intArrayOf(1, 4, 2, 2, 2, 4, 5, 2), // 4 create
        intArrayOf(6, 5, 5, 5, 5, 5, 5, 5), // 5 trigger
        intArrayOf(6, 6, 5, 5, 5, 5, 5, 7), // 6 semicolon inside a trigger
        intArrayOf(1, 7, 5, 5, 5, 5, 5, 5), // 7 end of a trigger
    )

    fun split(script: String): List<String> {
        val statements = mutableListOf<String>()
        var state = START
        var start = 0
        var hasContent = false
        var index = 0
        while (index < script.length) {
            val char = script[index]
            val next = script.getOrNull(index + 1)
            val token: Int
            when {
                char.isWhitespace() -> {
                    index++
                    token = WS
                }
                char == '-' && next == '-' -> {
                    index = script.indexOf('\n', index).let { if (it < 0) script.length else it + 1 }
                    token = WS
                }
                char == '/' && next == '*' -> {
                    index = script.indexOf("*/", index + 2).let { if (it < 0) script.length else it + 2 }
                    token = WS
                }
                char == '\'' || char == '"' || char == '`' -> {
                    index = script.indexOf(char, index + 1).let { if (it < 0) script.length else it + 1 }
                    token = OTHER
                }
                char == '[' -> {
                    index = script.indexOf(']', index + 1).let { if (it < 0) script.length else it + 1 }
                    token = OTHER
                }
                char == ';' -> {
                    index++
                    token = SEMI
                }
                isIdentifier(char) -> {
                    val end = (index until script.length).firstOrNull { !isIdentifier(script[it]) } ?: script.length
                    token = keyword(script.substring(index, end))
                    index = end
                }
                else -> {
                    index++
                    token = OTHER
                }
            }

            if (token != WS && token != SEMI) hasContent = true
            state = transitions[state][token]
            if (token == SEMI && state == START) {
                if (hasContent) statements += script.substring(start, index).trim()
                start = index
                hasContent = false
            }
        }

        if (hasContent) statements += script.substring(start).trim()
        return statements
    }

    private fun isIdentifier(char: Char) = char.isLetterOrDigit() || char == '_' || char == '$' || char.code >= 0x80

    private fun keyword(word: String) = when (word.lowercase(Locale.ROOT)) {
        "explain" -> EXPLAIN
        "create" -> CREATE
        "temp", "temporary" -> TEMP
        "trigger" -> TRIGGER
        "end" -> END
        else -> OTHER
    }
}
