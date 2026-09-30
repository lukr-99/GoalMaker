package com.goalmaker.app.domain.notes

/**
 * The light Markdown task notes and letters use (docs/archive.md, contracts/vectors/markdown.json):
 * lines, headings starting with "# " to "### ", list lines starting with "- " or "* ", **bold**,
 * *italic*, and bare http(s) links. Anything unmatched stays as it was typed.
 */
object LightMarkdown {
    private const val TRAILING = ".,;:!?)"
    private const val MAX_HEADING = 3
    private val schemes = listOf("https://", "http://")

    fun parse(text: String): List<MarkdownBlock> {
        if (text.isEmpty()) return emptyList()
        return text.replace("\r", "").split('\n').map { line ->
            val start = line.trimStart()
            val level = headingLevel(start)
            if (level > 0) {
                MarkdownBlock(bullet = false, spans = inline(start.substring(level + 1).trim()), heading = level)
            } else if (start.startsWith("- ") || start.startsWith("* ")) {
                MarkdownBlock(bullet = true, spans = inline(start.substring(2)))
            } else {
                MarkdownBlock(bullet = false, spans = inline(line))
            }
        }
    }

    // "# " to "### ": the level, or 0 when the line isn't a heading.
    private fun headingLevel(line: String): Int {
        val hashes = line.takeWhile { it == '#' }.length
        return if (hashes in 1..MAX_HEADING && line.length > hashes && line[hashes] == ' ') hashes else 0
    }

    private fun inline(line: String): List<MarkdownSpan> {
        val spans = mutableListOf<MarkdownSpan>()
        val plain = StringBuilder()
        fun flush() {
            if (plain.isNotEmpty()) spans += MarkdownSpan(plain.toString())
            plain.clear()
        }

        var index = 0
        while (index < line.length) {
            if (line.startsWith("**", index)) {
                val end = line.indexOf("**", index + 2)
                if (end > index + 2) {
                    flush()
                    spans += MarkdownSpan(line.substring(index + 2, end), bold = true)
                    index = end + 2
                } else {
                    plain.append("**")
                    index += 2
                }
                continue
            }
            if (line[index] == '*') {
                val end = line.indexOf('*', index + 1)
                if (end > index + 1 && line[index + 1] != ' ' && line[end - 1] != ' ') {
                    flush()
                    spans += MarkdownSpan(line.substring(index + 1, end), italic = true)
                    index = end + 1
                } else {
                    plain.append('*')
                    index += 1
                }
                continue
            }
            val scheme = schemes.firstOrNull { line.startsWith(it, index) }
            if (scheme != null) {
                var end = index
                while (end < line.length && !line[end].isWhitespace()) end++
                var url = line.substring(index, end)
                val trailing = StringBuilder()
                while (url.isNotEmpty() && url.last() in TRAILING) {
                    trailing.insert(0, url.last())
                    url = url.dropLast(1)
                }
                if (url.length > scheme.length) {
                    flush()
                    spans += MarkdownSpan(url, link = url)
                    plain.append(trailing)
                } else {
                    plain.append(line, index, end)
                }
                index = end
                continue
            }
            plain.append(line[index])
            index += 1
        }
        flush()
        return spans
    }
}
