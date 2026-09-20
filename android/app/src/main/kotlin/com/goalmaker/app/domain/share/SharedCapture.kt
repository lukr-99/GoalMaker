package com.goalmaker.app.domain.share

/**
 * What another app shared, split into the line the composer reads and the notes that keep where it
 * came from (spec, story 10). A shared link leaves its URL in the notes, so the item carries its
 * source; a shared passage keeps everything after its first line there.
 */
data class SharedCapture(val line: String, val notes: String) {
    companion object {
        private const val MAX_LINE = 500

        // A bare http(s) URL: everything up to whitespace, with trailing punctuation left out.
        private val URL = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
        private const val TRAILING = ".,;:!?)]}\"'"

        /**
         * The capture a share intent makes. [subject] is the sender's title, if it gave one, and
         * [text] what it shared. The first line becomes the composer line and everything else the
         * notes; a link goes to the notes whatever else the share said.
         */
        fun of(subject: String?, text: String?): SharedCapture {
            val shared = text?.trim().orEmpty()
            val title = subject?.trim().orEmpty()
            if (shared.isEmpty()) return SharedCapture(title.take(MAX_LINE), "")

            val link = URL.find(shared)?.value?.trimEnd { it in TRAILING }
            val withoutLink = if (link == null) shared else shared.replace(link, " ").trim()
            val body = if (withoutLink.isEmpty()) title else withoutLink
            val lines = body.lines().map(String::trim).filter(String::isNotEmpty)
            val line = lines.firstOrNull()
                ?: link?.let(::nameOf)
                ?: ""
            val rest = lines.drop(1)
            val notes = buildList {
                if (link != null) add(link)
                if (rest.isNotEmpty()) add(rest.joinToString("\n"))
            }.joinToString("\n\n")
            return SharedCapture(line.take(MAX_LINE), notes)
        }

        // A link with nothing said about it becomes its host and path: "example.com/an-article".
        private fun nameOf(link: String): String =
            link.substringAfter("://").trimEnd('/').ifEmpty { link }
    }
}
