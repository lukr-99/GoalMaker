package com.goalmaker.app.application.composer

import com.goalmaker.app.application.planning.GoalHorizon
import com.goalmaker.app.application.planning.GoalRules
import com.goalmaker.app.application.planning.HabitRules
import java.time.LocalDate
import java.util.Locale

/**
 * What the bottom bar on Wants, Habits and Goals reads from a typed line (docs/composer.md, "Adding on
 * Wants, Habits and Goals"; contracts/vectors/quick-add.json). A port of the connector's
 * supabase/functions/_shared/rules/quickAdd.ts. A line is read word by word: a word is what sits
 * between spaces, compared without case and without the punctuation at its end. Anything not
 * understood stays in the title.
 */
object QuickAddLines {
    /** The longest wait a want can pick, as in the want form. */
    const val MAX_WAIT_DAYS = 365

    // A word is free to read, used by what was read, or kept as text after it read as something out of range.
    private const val FREE = 0
    private const val USED = 1
    private const val KEPT = 2

    private val SPACES = Regex("[\\s\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]+")
    private val TRAILING = Regex("[.,;:!?]+$")
    private val WHOLE = Regex("^[0-9]+$")
    private val DECIMAL = Regex("^[0-9]+([.,][0-9]{1,2})?$")
    private val SEPARATOR = Regex("[.,]")
    private val GROUPED = Regex("^[0-9]{1,3}([.,][0-9]{3})+$")
    private val GROUP_HEAD = Regex("^[0-9]{1,3}$")
    private val GROUP = Regex("^[0-9]{3}$")
    private val JOINED = Regex("^([0-9][0-9.,]*)(\\p{L}+)$")
    private val JOINED_PRICE = Regex("^([0-9][0-9.,]*)(\\p{L}+|[€$£])$")
    private val UNIT = Regex("^\\p{L}[\\p{L}-]*$")
    private val NX = Regex("^([0-9]+)x$")
    private val YEAR = Regex("^[0-9]{4}$")

    private val CURRENCIES = mapOf(
        "kč" to "CZK", "kc" to "CZK", "czk" to "CZK",
        "€" to "EUR", "eur" to "EUR", "euro" to "EUR", "euros" to "EUR",
        "$" to "USD", "usd" to "USD",
        "£" to "GBP", "gbp" to "GBP",
    )
    private val SIGNS = listOf("€", "$", "£")
    private val WAIT_UNITS = mapOf("day" to 1, "days" to 1, "week" to 7, "weeks" to 7, "month" to 30, "months" to 30)
    private val PER = setOf("a", "per", "each", "every")
    private val DAY_NAMES = mapOf(
        "mon" to 1, "monday" to 1,
        "tue" to 2, "tues" to 2, "tuesday" to 2,
        "wed" to 4, "wednesday" to 4,
        "thu" to 8, "thur" to 8, "thurs" to 8, "thursday" to 8,
        "fri" to 16, "friday" to 16,
        "sat" to 32, "saturday" to 32,
        "sun" to 64, "sunday" to 64,
    )
    private const val WORK_WEEK = 31
    private const val WEEKEND = 96
    private val MONTHS = mapOf(
        "january" to 1, "jan" to 1, "february" to 2, "feb" to 2, "march" to 3, "mar" to 3,
        "april" to 4, "apr" to 4, "may" to 5, "june" to 6, "jun" to 6, "july" to 7, "jul" to 7,
        "august" to 8, "aug" to 8, "september" to 9, "sep" to 9, "sept" to 9, "october" to 10, "oct" to 10,
        "november" to 11, "nov" to 11, "december" to 12, "dec" to 12,
    )

    /** Small words that never name a unit, so "2 of my friends" is no amount. */
    private val NOT_UNITS = setOf(
        "a", "an", "the", "of", "and", "or", "to", "in", "on", "at", "for", "per", "each", "every", "x",
        "day", "days", "week", "weeks", "month", "months", "year", "years",
    )

    /** Units a habit measures as an amount (asked for its value); any other unit is a count (+1 a tap). */
    private val AMOUNT_UNITS = setOf(
        "min", "mins", "minute", "minutes", "h", "hr", "hrs", "hour", "hours", "s", "sec", "secs", "second", "seconds",
        "km", "kms", "mi", "mile", "miles", "m", "meter", "meters", "metre", "metres", "step", "steps", "page", "pages",
        "ml", "l", "liter", "liters", "litre", "litres", "kcal", "cal", "kg", "g",
    )

    /** A want: "Kindle 3290 Kč wait 2 weeks because I read on the train". */
    fun readWant(line: String): WantLine {
        val all = words(line)
        val cut = all.indexOfFirst { it.key == "because" }
        val reason = if (cut < 0) "" else all.drop(cut + 1).joinToString(" ") { it.text }
        val read = Reader(if (cut < 0) all else all.take(cut))
        var price: Double? = null
        var currency: String? = null
        var waitDays: Int? = null

        var i = 0
        while (i < read.words.size && price == null) {
            val key = read.key(i)
            if (key != null) {
                val sign = SIGNS.firstOrNull { key.startsWith(it) }
                val joined = JOINED_PRICE.find(key)
                val signed = sign?.let { parseNumber(key.substring(it.length)) }
                if (sign != null && signed != null) {
                    price = signed.value
                    currency = CURRENCIES.getValue(sign)
                    read.mark(i, i + 1, USED)
                } else if (joined != null && CURRENCIES[joined.groupValues[2]] != null && parseNumber(joined.groupValues[1]) != null) {
                    price = parseNumber(joined.groupValues[1])!!.value
                    currency = CURRENCIES.getValue(joined.groupValues[2])
                    read.mark(i, i + 1, USED)
                } else {
                    val number = read.number(i)
                    val code = number?.let { read.key(it.end) }
                    if (number != null && code != null && CURRENCIES[code] != null) {
                        price = number.value
                        currency = CURRENCIES.getValue(code)
                        read.mark(i, number.end + 1, USED)
                    }
                }
            }
            i++
        }

        i = 0
        while (i < read.words.size && waitDays == null) {
            if (read.key(i) == "wait") {
                var j = i + 1
                if (read.key(j) == "for") j++
                val amount = read.key(j)
                val n = when {
                    amount == null -> null
                    WHOLE.matches(amount) -> amount.toDouble()
                    amount in setOf("a", "an", "one") -> 1.0
                    else -> null
                }
                val per = WAIT_UNITS[read.key(j + 1) ?: ""]
                if (n != null && per != null) {
                    val days = n * per
                    if (days >= 1 && days <= MAX_WAIT_DAYS) {
                        waitDays = days.toInt()
                        read.mark(i, j + 2, USED)
                    } else {
                        read.mark(i, j + 2, KEPT)
                    }
                }
            }
            i++
        }

        return WantLine(read.rest(), reason.ifEmpty { null }, price, currency, waitDays)
    }

    /**
     * A habit: "Swim 2 times a week", "Read 20 minutes every day", "Piano every mon and thu", or a limit:
     * "Coffee at most 3 cups a day", "At most 2 takeaways a week", "No casino this month".
     */
    fun readHabit(line: String): HabitLine {
        val read = Reader(words(line))
        var cadence = HabitRules.DAILY
        var weekdays: Int? = null
        var times: Int? = null
        var measure = HabitRules.CHECK
        var target: Double? = null
        var unit: String? = null

        val limit = limitAt(read)
        val direction = if (limit == Limit.NONE) HabitRules.AT_LEAST else HabitRules.AT_MOST

        for (i in read.words.indices) {
            val found = cadenceAt(read, i, limit) ?: continue
            if (!found.fits) {
                read.mark(i, found.end, KEPT)
                continue
            }
            cadence = found.cadence
            weekdays = found.weekdays
            times = found.times?.toInt()
            read.mark(i, found.end, USED)
            break
        }

        for (i in read.words.indices) {
            val key = read.key(i) ?: continue
            val count: Pair<Double, Int>? = when {
                WHOLE.matches(key) && key.toDouble() >= 1 && (read.key(i + 1) ?: "") in setOf("times", "time") -> key.toDouble() to i + 2
                (key == "once" || key == "twice") && read.perDay(i + 1) > 0 -> (if (key == "once") 1.0 else 2.0) to i + 1
                else -> null
            }
            if (count != null) {
                measure = HabitRules.COUNT
                target = count.first
                read.mark(i, count.second + read.perDay(count.second), USED)
                break
            }
            val found = read.measure(i)
            if (found == null || found.value < 0 || (found.value == 0.0 && limit == Limit.NONE)) continue
            measure = if (found.decimal || found.unit.lowercase(Locale.ROOT) in AMOUNT_UNITS) HabitRules.AMOUNT else HabitRules.COUNT
            target = found.value
            unit = found.unit
            // Under a limit, "a week" or "a month" after the number makes it the most for the whole period.
            val period = if (limit == Limit.NONE || cadence != HabitRules.DAILY) null else periodAt(read, found.end)
            if (period != null) {
                cadence = if (period.week) HabitRules.PER_WEEK else HabitRules.PER_MONTH
                times = 1
                read.mark(i, period.end, USED)
            } else {
                read.mark(i, found.end + read.perDay(found.end), USED)
            }
            break
        }

        return HabitLine(read.rest(), cadence, weekdays, times, measure, target, unit, direction)
    }

    /** A goal: "Run 30 km this week", "Read 3 books in November". */
    fun readGoal(line: String, today: LocalDate): GoalLine {
        val read = Reader(words(line))
        var horizon = GoalHorizon.WEEK
        var start = GoalRules.periodStart(GoalHorizon.WEEK, today)
        fun next(h: GoalHorizon) = GoalRules.periodStart(h, GoalRules.periodEnd(h, GoalRules.periodStart(h, today)).plusDays(1))

        for (i in read.words.indices) {
            val key = read.key(i) ?: continue
            val second = read.key(i + 1) ?: ""
            val period = GoalHorizon.of(second)?.takeIf { it != GoalHorizon.DAY }
            val found: Triple<GoalHorizon, LocalDate, Int>? = when {
                (key == "this" || key == "next") && period != null ->
                    Triple(period, if (key == "this") GoalRules.periodStart(period, today) else next(period), 2)
                key == "today" -> Triple(GoalHorizon.DAY, today, 1)
                key == "tomorrow" -> Triple(GoalHorizon.DAY, today.plusDays(1), 1)
                key == "in" && MONTHS[second] != null -> {
                    val month = MONTHS.getValue(second)
                    Triple(GoalHorizon.MONTH, LocalDate.of(if (month < today.monthValue) today.year + 1 else today.year, month, 1), 2)
                }
                key == "in" && YEAR.matches(second) -> {
                    if (second.toInt() < today.year) {
                        read.mark(i, i + 2, KEPT)
                        continue
                    }
                    Triple(GoalHorizon.YEAR, LocalDate.of(second.toInt(), 1, 1), 2)
                }
                else -> null
            }
            if (found == null) continue
            horizon = found.first
            start = found.second
            read.mark(i, i + found.third, USED)
            break
        }

        var target: Double? = null
        var unit: String? = null
        var i = 0
        while (i < read.words.size && target == null) {
            val found = read.measure(i)
            if (found != null && found.value > 0) {
                target = found.value
                unit = found.unit
            }
            i++
        }

        return GoalLine(read.rest(), horizon, start, if (target == null) GoalRules.MODE_DONE else GoalRules.MODE_NUMBER, target, unit)
    }

    private class Word(val text: String, val key: String)

    private class ReadNumber(val value: Double, val decimal: Boolean, val end: Int)

    private class Measure(val value: Double, val decimal: Boolean, val unit: String, val end: Int)

    private class Cadence(val cadence: String, val weekdays: Int?, val times: Double?, val end: Int, val fits: Boolean)

    private class Period(val week: Boolean, val end: Int)

    /** How a line sets a limit: not at all, with a number to come ("at most"), or as not once ("no"). */
    private enum class Limit { NONE, MOST, ZERO }

    private fun words(line: String): List<Word> = line.split(SPACES).filter { it.isNotEmpty() }.map { text ->
        Word(text, text.lowercase(Locale.ROOT).replace(TRAILING, ""))
    }

    /** A number as written: one or two decimals after a dot or comma, or groups of three after one. */
    private fun parseNumber(text: String): ReadNumber? = when {
        DECIMAL.matches(text) -> ReadNumber(text.replaceFirst(',', '.').toDouble(), SEPARATOR.containsMatchIn(text), 0)
        GROUPED.matches(text) -> ReadNumber(text.replace(SEPARATOR, "").toDouble(), false, 0)
        else -> null
    }

    private class Reader(val words: List<Word>) {
        // Every word starts FREE (0).
        private val state = IntArray(words.size)

        fun key(i: Int): String? = if (i < words.size && state[i] == FREE) words[i].key else null

        fun mark(from: Int, to: Int, value: Int) {
            for (i in from until minOf(to, words.size)) state[i] = value
        }

        fun rest(): String = words.filterIndexed { i, _ -> state[i] != USED }.joinToString(" ") { it.text }

        /** A number from word [i], maybe across words ("10 000"); its end is the word after it. */
        fun number(i: Int): ReadNumber? {
            val head = key(i) ?: return null
            if (GROUP_HEAD.matches(head)) {
                var end = i + 1
                val digits = StringBuilder(head)
                while (key(end)?.let(GROUP::matches) == true) digits.append(key(end++))
                if (end > i + 1) return ReadNumber(digits.toString().toDouble(), false, end)
            }
            val one = parseNumber(head) ?: return null
            return ReadNumber(one.value, one.decimal, i + 1)
        }

        /** A unit word at [i]: letters (a hyphen inside is fine), and not one of the small words. */
        fun unit(i: Int): String? {
            if (key(i) == null) return null
            val text = words[i].text.replace(TRAILING, "")
            return if (UNIT.matches(text) && text.length <= 20 && text.lowercase(Locale.ROOT) !in NOT_UNITS) text else null
        }

        /** A number and its unit from word [i], apart ("30 min") or together ("30min"). */
        fun measure(i: Int): Measure? {
            val number = number(i)
            if (number != null) {
                val unit = unit(number.end) ?: return null
                return Measure(number.value, number.decimal, unit, number.end + 1)
            }
            if (key(i) == null) return null
            val joined = JOINED.find(words[i].text.replace(TRAILING, "")) ?: return null
            val value = parseNumber(joined.groupValues[1])
            val unit = joined.groupValues[2]
            if (value == null || unit.lowercase(Locale.ROOT) in NOT_UNITS) return null
            return Measure(value.value, value.decimal, unit, i + 1)
        }

        /** "a day", "per day" or "each day" at [i]: how many words, or 0. */
        fun perDay(i: Int): Int = if ((key(i) ?: "") in PER && key(i + 1) == "day") 2 else 0
    }

    /**
     * The first limit phrase, marked as used: "at most", "max", "maximum", "no more than" or "not more
     * than" anywhere, or "no" or "never" as the line's first word.
     */
    private fun limitAt(read: Reader): Limit {
        for (i in read.words.indices) {
            fun k(offset: Int) = read.key(i + offset) ?: ""
            val length = when {
                k(0) == "at" && k(1) == "most" -> 2
                k(0) == "max" || k(0) == "maximum" -> 1
                (k(0) == "no" || k(0) == "not") && k(1) == "more" && k(2) == "than" -> 3
                else -> 0
            }
            if (length > 0) {
                read.mark(i, i + length, USED)
                return Limit.MOST
            }
        }
        if (read.key(0) == "no" || read.key(0) == "never") {
            read.mark(0, 1, USED)
            return Limit.ZERO
        }
        return Limit.NONE
    }

    /** "a week", "per month", "this week" and the like at [i]: which period, and where it ends. */
    private fun periodAt(read: Reader, i: Int): Period? {
        val first = read.key(i) ?: ""
        val second = read.key(i + 1) ?: ""
        if (!(first in PER || first == "this") || !(second == "week" || second == "month")) return null
        return Period(second == "week", i + 2)
    }

    private fun cadenceAt(read: Reader, i: Int, limit: Limit): Cadence? {
        val key = read.key(i) ?: return null
        fun k(offset: Int) = read.key(i + offset) ?: ""
        fun per(n: Double, period: String, end: Int): Cadence {
            val week = period == "week"
            val fits = n >= (if (limit == Limit.NONE) 1 else 0) && n <= (if (week) 7 else 31)
            return Cadence(if (week) HabitRules.PER_WEEK else HabitRules.PER_MONTH, null, n, end, fits)
        }
        fun period(word: String) = word == "week" || word == "month"

        if (limit != Limit.NONE && WHOLE.matches(key)) {
            // Under a limit a bare number, or a number of days, is how many days the period may have.
            if (k(1) in PER && period(k(2))) return per(key.toDouble(), k(2), i + 3)
            if (k(1) in setOf("days", "day") && k(2) in PER && period(k(3))) return per(key.toDouble(), k(3), i + 4)
        }
        if (limit == Limit.ZERO) {
            // "No casino this month": the period alone, and not once in it.
            val alone = periodAt(read, i)
            if (alone != null) return per(0.0, if (alone.week) "week" else "month", alone.end)
        }

        val nx = NX.find(key)
        if (WHOLE.matches(key) && k(1) in setOf("x", "times", "time") && k(2) in PER && period(k(3))) {
            return per(key.toDouble(), k(3), i + 4)
        }
        if (nx != null && k(1) in PER && period(k(2))) return per(nx.groupValues[1].toDouble(), k(2), i + 3)
        if ((key == "once" || key == "twice") && k(1) in PER && period(k(2))) {
            return per(if (key == "once") 1.0 else 2.0, k(2), i + 3)
        }
        if (key == "weekly" || key == "monthly") return per(1.0, if (key == "weekly") "week" else "month", i + 1)

        if (key == "daily") return Cadence(HabitRules.DAILY, null, null, i + 1, true)
        if ((key == "every" || key == "each") && k(1) == "day") return Cadence(HabitRules.DAILY, null, null, i + 2, true)

        fun days(mask: Int, end: Int) = Cadence(HabitRules.WEEKDAYS, mask, null, end, true)
        if (key == "weekdays") return days(WORK_WEEK, i + 1)
        if (key == "weekends") return days(WEEKEND, i + 1)
        if (key == "every" || key == "on") {
            if (k(1) == "weekday" || k(1) == "weekdays") return days(WORK_WEEK, i + 2)
            if (k(1) == "weekend" || k(1) == "weekends") return days(WEEKEND, i + 2)
            var mask = DAY_NAMES[k(1)] ?: return null
            var j = i + 1
            while (true) {
                if (DAY_NAMES[read.key(j + 1) ?: ""] != null) {
                    j += 1
                } else if ((read.key(j + 1) ?: "-") in setOf("and", "&", "") && DAY_NAMES[read.key(j + 2) ?: ""] != null) {
                    j += 2
                } else {
                    break
                }
                mask = mask or DAY_NAMES.getValue(read.key(j)!!)
            }
            return days(mask, j + 1)
        }
        return null
    }
}
