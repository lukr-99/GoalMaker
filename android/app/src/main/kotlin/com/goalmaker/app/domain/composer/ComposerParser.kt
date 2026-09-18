package com.goalmaker.app.domain.composer

import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Turns one composer line into a draft (docs/composer.md), pinned by
 * contracts/vectors/composer.json. Pure: the caller passes the local time and the day rollover hour.
 */
object ComposerParser {
    val KNOWN_COMMANDS = setOf("plan", "review", "habit", "goal")

    private const val TRAILING = ".,;:!?"
    private val markerName = Regex("\\p{L}[\\p{L}\\p{N}_-]*")
    private val clock24 = Regex("(\\d{1,2}):(\\d{2})")
    private val clock12 = Regex("(\\d{1,2})(?::(\\d{2}))?(am|pm)")
    private val hourOnly = Regex("\\d{1,2}")
    private val isoDate = Regex("(\\d{4})-(\\d{2})-(\\d{2})")
    private val dottedDate = Regex("(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})?")
    private val dayOfMonth = Regex("(\\d{1,2})(st|nd|rd|th)?")
    private val ordinal = Regex("(\\d{1,2})(st|nd|rd|th)")
    private val count = Regex("\\d{1,3}")

    private val weekdays = mapOf(
        "monday" to DayOfWeek.MONDAY, "mon" to DayOfWeek.MONDAY,
        "tuesday" to DayOfWeek.TUESDAY, "tue" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY,
        "wednesday" to DayOfWeek.WEDNESDAY, "wed" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY, "thu" to DayOfWeek.THURSDAY, "thur" to DayOfWeek.THURSDAY,
        "thurs" to DayOfWeek.THURSDAY,
        "friday" to DayOfWeek.FRIDAY, "fri" to DayOfWeek.FRIDAY,
        "saturday" to DayOfWeek.SATURDAY, "sat" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY, "sun" to DayOfWeek.SUNDAY,
    )
    private val months = listOf(
        "january", "february", "march", "april", "may", "june",
        "july", "august", "september", "october", "november", "december",
    ).flatMapIndexed { index, name -> listOf(name to index + 1, name.take(3) to index + 1) }.toMap() + ("sept" to 9)
    private val workWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    private val weekend = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

    fun parse(line: String, now: LocalDateTime, rolloverHour: Int = 4): ComposerDraft {
        command(line)?.let { return it }

        val tokens = tokenize(line)
        val today = now.minusHours(rolloverHour.toLong()).toLocalDate()
        val markers = tokens.map(::marker)
        val candidates = tokens.indices.flatMap { start -> phrasesAt(tokens, start, today) }

        // Dates, times and repeats count in the line's tail, then at its start (docs/composer.md).
        val accepted = mutableListOf<Phrase>()
        var index = tokens.lastIndex
        while (index >= 0) {
            if (markers[index] != null) {
                index--
                continue
            }
            val phrase = candidates.filter { it.end == index + 1 }.minByOrNull { it.start } ?: break
            accepted += phrase
            index = phrase.start - 1
        }
        val headLimit = index
        var position = 0
        while (position <= headLimit) {
            if (markers[position] != null) {
                position++
                continue
            }
            val phrase = candidates.filter { it.start == position && it.end - 1 <= headLimit }.maxByOrNull { it.end } ?: break
            accepted += phrase
            position = phrase.end
        }

        val consumed = BooleanArray(tokens.size)
        markers.forEachIndexed { i, marker -> if (marker != null) consumed[i] = true }
        accepted.forEach { phrase -> (phrase.start until phrase.end).forEach { consumed[it] = true } }

        // The last one wins (docs/composer.md): the phrase that starts furthest right.
        fun <T> last(kind: SpanKind, value: (Phrase) -> T?): T? =
            accepted.filter { it.kind == kind }.maxByOrNull { it.start }?.let(value)
        val date = last(SpanKind.DATE) { it.date }
        val time = last(SpanKind.TIME) { it.time }
        val repeat = last(SpanKind.REPEAT) { it.repeat }
        val earliest = if (time != null && date == null && LocalDateTime.of(today, time).isBefore(now)) today.plusDays(1) else today
        val planned = date ?: repeat?.firstOccurrence(earliest) ?: earliest.takeIf { time != null }

        val tags = LinkedHashMap<String, String>()
        markers.filterNotNull().filter { it.kind == SpanKind.TAG }.forEach { tags.putIfAbsent(it.name.lowercase(Locale.ROOT), it.name) }

        val spans = tokens.indices.mapNotNull { i -> markers[i]?.let { ComposerSpan(it.kind, tokens[i].start, tokens[i].end) } } +
            accepted.map { ComposerSpan(it.kind, tokens[it.start].start, tokens[it.end - 1].end) }

        return ComposerDraft(
            title = tokens.filterIndexed { i, _ -> !consumed[i] }.joinToString(" ") { it.text },
            plannedDate = planned,
            plannedTime = time,
            tags = tags.values.toList(),
            area = markers.lastOrNull { it?.kind == SpanKind.AREA }?.name?.replace('_', ' '),
            project = markers.lastOrNull { it?.kind == SpanKind.PROJECT }?.name?.replace('_', ' '),
            topPriority = markers.any { it?.kind == SpanKind.PRIORITY },
            idea = markers.any { it?.kind == SpanKind.IDEA },
            repeat = repeat?.rule(planned ?: today),
            spans = spans.sortedBy { it.start },
        )
    }

    private fun command(line: String): ComposerDraft? {
        val trimmed = line.trimStart()
        if (trimmed.length < 2 || trimmed[0] != '/' || !trimmed[1].isLetter()) return null
        val offset = line.length - trimmed.length
        val nameEnd = trimmed.indexOfFirst { it.isWhitespace() }.let { if (it < 0) trimmed.length else it }
        val name = trimmed.substring(1, nameEnd).lowercase(Locale.ROOT)
        return ComposerDraft(
            title = "",
            command = ComposerCommand(name, trimmed.substring(nameEnd).trim(), name in KNOWN_COMMANDS),
            spans = listOf(ComposerSpan(SpanKind.COMMAND, offset, offset + nameEnd)),
        )
    }

    private class Token(val raw: String, val start: Int, val end: Int) {
        /** A leading backslash keeps the word as text. */
        val escaped = raw.length > 1 && raw[0] == '\\'
        val text = if (escaped) raw.substring(1) else raw

        /** For matching words: lowercase, without trailing punctuation; null when escaped. */
        val word: String? = if (escaped) null else raw.lowercase(Locale.ROOT).trimEnd { it in TRAILING }

        /** For numeric dates, whose dots matter. */
        val numeric: String? = if (escaped) null else raw.lowercase(Locale.ROOT).trimEnd { it in ",;:!?" }
    }

    private class Marker(val kind: SpanKind, val name: String)

    private class Phrase(
        val kind: SpanKind,
        val start: Int,
        val end: Int,
        val date: LocalDate? = null,
        val time: LocalTime? = null,
        val repeat: Repeat? = null,
    )

    private fun tokenize(line: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var index = 0
        while (index < line.length) {
            if (line[index].isWhitespace()) {
                index++
                continue
            }
            val start = index
            while (index < line.length && !line[index].isWhitespace()) index++
            tokens += Token(line.substring(start, index), start, index)
        }
        return tokens
    }

    private fun marker(token: Token): Marker? {
        if (token.escaped) return null
        when (token.raw) {
            "!" -> return Marker(SpanKind.PRIORITY, "")
            "?" -> return Marker(SpanKind.IDEA, "")
        }
        val core = token.raw.trimEnd { it in TRAILING }
        if (core.length < 2 || !markerName.matches(core.substring(1))) return null
        val kind = when (core[0]) {
            '#' -> SpanKind.TAG
            '@' -> SpanKind.AREA
            '+' -> SpanKind.PROJECT
            else -> return null
        }
        return Marker(kind, core.substring(1))
    }

    private fun phrasesAt(tokens: List<Token>, start: Int, today: LocalDate): List<Phrase> {
        fun word(i: Int) = tokens.getOrNull(i)?.word
        val found = mutableListOf<Phrase>()
        time(tokens, start)?.let { (end, time) -> found += Phrase(SpanKind.TIME, start, end, time = time) }
        date(tokens, start, today, allowOn = true)?.let { (end, date) -> found += Phrase(SpanKind.DATE, start, end, date = date) }
        repeat(tokens, start, ::word)?.let { (end, repeat) -> found += Phrase(SpanKind.REPEAT, start, end, repeat = repeat) }
        return found
    }

    private fun time(tokens: List<Token>, start: Int): Pair<Int, LocalTime>? {
        fun word(i: Int) = tokens.getOrNull(i)?.word
        val at = if (word(start) == "at") start + 1 else start
        val first = word(at) ?: return null
        val suffix = word(at + 1)?.takeIf { it == "am" || it == "pm" }
        when (first) {
            "noon" -> return at + 1 to LocalTime.NOON
            "midnight" -> return at + 1 to LocalTime.MIDNIGHT
        }
        clock12.matchEntire(first)?.let { match ->
            return twelveHour(match.groupValues[1].toInt(), match.groupValues[2].ifEmpty { "0" }.toInt(), match.groupValues[3])
                ?.let { at + 1 to it }
        }
        clock24.matchEntire(first)?.let { match ->
            val hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toInt()
            if (suffix != null) twelveHour(hour, minute, suffix)?.let { return at + 2 to it }
            return if (hour <= 23 && minute <= 59) at + 1 to LocalTime.of(hour, minute) else null
        }
        if (suffix != null && hourOnly.matches(first)) {
            return twelveHour(first.toInt(), 0, suffix)?.let { at + 2 to it }
        }
        return null
    }

    private fun twelveHour(hour: Int, minute: Int, suffix: String): LocalTime? {
        if (hour !in 1..12 || minute > 59) return null
        val converted = when {
            suffix == "am" && hour == 12 -> 0
            suffix == "am" -> hour
            hour == 12 -> 12
            else -> hour + 12
        }
        return LocalTime.of(converted, minute)
    }

    private fun date(tokens: List<Token>, start: Int, today: LocalDate, allowOn: Boolean): Pair<Int, LocalDate>? {
        fun word(i: Int) = tokens.getOrNull(i)?.word
        val first = word(start) ?: return null
        val nextMonday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        when (first) {
            "today" -> return start + 1 to today
            "tomorrow", "tmrw", "tmr" -> return start + 1 to today.plusDays(1)
            "on" -> return if (allowOn) date(tokens, start + 1, today, allowOn = false)?.takeIf { word(start + 1) !in setOf("today", "tomorrow", "tmrw", "tmr") } else null
            "next" -> {
                val second = word(start + 1)
                weekdays[second]?.let { return start + 2 to nextMonday.plusDays((it.value - 1).toLong()) }
                return when (second) {
                    "week" -> start + 2 to nextMonday
                    "month" -> start + 2 to today.withDayOfMonth(1).plusMonths(1)
                    else -> null
                }
            }
            "in" -> {
                val amount = word(start + 1)?.let { if (it == "a" || it == "an") 1 else if (count.matches(it)) it.toInt() else null }
                    ?: return null
                if (amount < 1) return null
                return when (word(start + 2)) {
                    "day", "days" -> start + 3 to today.plusDays(amount.toLong())
                    "week", "weeks" -> start + 3 to today.plusWeeks(amount.toLong())
                    "month", "months" -> start + 3 to today.plusMonths(amount.toLong())
                    else -> null
                }
            }
        }
        weekdays[first]?.let { return start + 1 to today.with(TemporalAdjusters.next(it)) }

        val numeric = tokens[start].numeric ?: return null
        isoDate.matchEntire(numeric)?.let { match ->
            return dateOf(match.groupValues[1].toInt(), match.groupValues[2].toInt(), match.groupValues[3].toInt())?.let { start + 1 to it }
        }
        dottedDate.matchEntire(numeric)?.let { match ->
            val day = match.groupValues[1].toInt()
            val month = match.groupValues[2].toInt()
            val year = match.groupValues[3]
            val resolved = if (year.isNotEmpty()) dateOf(year.toInt(), month, day) else upcoming(month, day, today)
            return resolved?.let { start + 1 to it }
        }

        // "25 october", "sep 25", "sep 25th"
        val second = word(start + 1) ?: return null
        val dayFirst = dayOfMonth.matchEntire(first)?.groupValues?.get(1)?.toInt()?.let { day -> months[second]?.let { day to it } }
        val monthFirst = months[first]?.let { month -> dayOfMonth.matchEntire(second)?.groupValues?.get(1)?.toInt()?.let { it to month } }
        val (day, month) = dayFirst ?: monthFirst ?: return null
        return upcoming(month, day, today)?.let { start + 2 to it }
    }

    private fun dateOf(year: Int, month: Int, day: Int): LocalDate? = try {
        LocalDate.of(year, month, day)
    } catch (_: DateTimeException) {
        null
    }

    /** A date without a year: this year's, or next year's when this year's has passed. */
    private fun upcoming(month: Int, day: Int, today: LocalDate): LocalDate? =
        dateOf(today.year, month, day)?.takeIf { !it.isBefore(today) } ?: dateOf(today.year + 1, month, day)

    private fun repeat(tokens: List<Token>, start: Int, word: (Int) -> String?): Pair<Int, Repeat>? {
        when (word(start)) {
            "daily" -> return start + 1 to Repeat(Repeat.Frequency.DAILY)
            "weekdays" -> return start + 1 to Repeat(Repeat.Frequency.WEEKLY, days = workWeek)
            "weekly" -> return start + 1 to Repeat(Repeat.Frequency.WEEKLY)
            "monthly" -> return start + 1 to Repeat(Repeat.Frequency.MONTHLY)
            "every" -> Unit
            else -> return null
        }
        val first = word(start + 1) ?: return null
        when (first) {
            "day" -> return start + 2 to Repeat(Repeat.Frequency.DAILY)
            "weekday" -> return start + 2 to Repeat(Repeat.Frequency.WEEKLY, days = workWeek)
            "weekend" -> return start + 2 to Repeat(Repeat.Frequency.WEEKLY, days = weekend)
            "week" -> return start + 2 to Repeat(Repeat.Frequency.WEEKLY)
            "month" -> return start + 2 to Repeat(Repeat.Frequency.MONTHLY)
        }
        if (count.matches(first)) {
            val interval = first.toInt()
            if (interval < 1) return null
            val frequency = when (word(start + 2)) {
                "day", "days" -> Repeat.Frequency.DAILY
                "week", "weeks" -> Repeat.Frequency.WEEKLY
                "month", "months" -> Repeat.Frequency.MONTHLY
                else -> return null
            }
            return start + 3 to Repeat(frequency, interval = interval)
        }
        ordinal.matchEntire(first)?.let { match ->
            val day = match.groupValues[1].toInt()
            return if (day in 1..31) start + 2 to Repeat(Repeat.Frequency.MONTHLY, monthDay = day) else null
        }

        // "every monday", "every mon, wed and fri"
        val days = linkedSetOf<DayOfWeek>()
        var index = start + 1
        while (true) {
            val day = weekdays[word(index)] ?: break
            days += day
            index++
            if (word(index) == "and" && weekdays[word(index + 1)] != null) index++
        }
        return if (days.isEmpty()) null else index to Repeat(Repeat.Frequency.WEEKLY, days = days)
    }
}
