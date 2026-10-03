package com.goalmaker.app.application.planning

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale

/**
 * The Tally place's closer look at one device's own time (docs/tally.md, ADR 0013), pinned by
 * contracts/vectors/tally.json ('window', 'hours', 'apps'), which the Windows app runs too: when in
 * the planning day the time went, by hour, and which apps (on the PC, which sites and folders) made
 * up each category. It works on the device's raw record and its results never sync.
 */
object TallyBreakdown {
    private const val HOUR = 3_600L
    private const val DAY = 24 * HOUR
    private val BROWSERS = setOf("chrome.exe", "msedge.exe", "firefox.exe", "brave.exe", "opera.exe", "vivaldi.exe", "arc.exe")
    private val EDITORS = setOf("code.exe", "studio64.exe", "devenv.exe")

    // Where a browser's title is cut: a hyphen, a bar, a middle dot, an en dash or an em dash between spaces.
    private val SEPARATOR = Regex(" (?:-|\\||·|–|—) ")

    // A count of new things a site puts first, like "(3) ".
    private val COUNT = Regex("""^\(\d+\+?\)\s*""")

    /**
     * What an app row shows under it: an editor's folder, or the site a browser's tab is on (the part of
     * the title just before the browser's own name). Null for any other app and for no title.
     */
    fun windowLabel(app: String, title: String?): String? {
        val text = title.orEmpty().trim()
        if (text.isEmpty()) return null
        val name = app.trim().lowercase(Locale.ROOT)
        if (name in EDITORS) return TallyRules.editorFolder(app, title)
        if (name !in BROWSERS) return null
        val parts = text.split(SEPARATOR)
        if (parts.size < 2) return null
        return parts[parts.size - 2].trim().replaceFirst(COUNT, "").trim().takeIf(String::isNotEmpty)
    }

    /**
     * The 24 clock hours of the planning [day], from [startHour] on, each with the seconds the
     * [stretches] spent in it by category, most first. Time two stretches share counts once, for the
     * one that started first, as in [TallyRules.dayTotals].
     */
    fun hours(stretches: List<TallyStretch>, day: LocalDate, startHour: Int): List<TallyHour> {
        val from = secondsOf(day.atTime(startHour, 0))
        val slots = List(24) { LinkedHashMap<String, Long>() }
        for ((start, end, stretch) in once(stretches, from, from + DAY)) {
            var at = start
            while (at < end) {
                val index = ((at - from) / HOUR).toInt()
                val until = minOf(end, from + (index + 1) * HOUR)
                slots[index].merge(stretch.category, until - at) { a, b -> a + b }
                at = until
            }
        }
        return slots.mapIndexed { index, slot ->
            TallyHour(
                hour = (startHour + index) % 24,
                seconds = slot.values.sum().toInt(),
                categories = slot.map { (category, seconds) -> TallySeconds(category, seconds.toInt()) }
                    .sortedWith(compareByDescending<TallySeconds> { it.seconds }.thenBy { it.category }),
            )
        }
    }

    /**
     * The [stretches] from the planning day [from] to [to], both included, by category, then app, then
     * site or folder ([windowLabel]): each one's seconds rounded to the nearest minute, those with none
     * left out, the most first and then by name. Time two stretches share counts once.
     */
    fun apps(stretches: List<TallyStretch>, from: LocalDate, to: LocalDate, startHour: Int): List<TallyCategoryApps> {
        val categories = LinkedHashMap<String, Long>()
        val apps = LinkedHashMap<Pair<String, String>, Long>()
        val windows = LinkedHashMap<Triple<String, String, String>, Long>()
        val window = once(stretches, secondsOf(from.atTime(startHour, 0)), secondsOf(to.plusDays(1).atTime(startHour, 0)))
        for ((start, end, stretch) in window) {
            val seconds = end - start
            val app = stretch.app.trim().lowercase(Locale.ROOT)
            categories.merge(stretch.category, seconds) { a, b -> a + b }
            apps.merge(stretch.category to app, seconds) { a, b -> a + b }
            windowLabel(stretch.app, stretch.title)?.let { label -> windows.merge(Triple(stretch.category, app, label), seconds) { a, b -> a + b } }
        }
        return categories.mapNotNull { (category, seconds) ->
            TallyCategoryApps(
                category = category,
                minutes = minutes(seconds),
                apps = apps.filterKeys { it.first == category }.mapNotNull { (key, appSeconds) ->
                    TallyAppTime(
                        app = key.second,
                        minutes = minutes(appSeconds),
                        windows = windows.filterKeys { it.first == category && it.second == key.second }
                            .map { (label, labelSeconds) -> TallyWindowTime(label.third, minutes(labelSeconds)) }
                            .filter { it.minutes > 0 }
                            .sortedWith(compareByDescending<TallyWindowTime> { it.minutes }.thenBy { it.label }),
                    ).takeIf { it.minutes > 0 }
                }.sortedWith(compareByDescending<TallyAppTime> { it.minutes }.thenBy { it.app }),
            ).takeIf { it.minutes > 0 }
        }.sortedWith(compareByDescending<TallyCategoryApps> { it.minutes }.thenBy { it.category })
    }

    // The stretches in start order as seconds from [from] to [to], each without the time an earlier one already had.
    private fun once(stretches: List<TallyStretch>, from: Long, to: Long): List<Triple<Long, Long, TallyStretch>> {
        var covered = Long.MIN_VALUE
        return stretches.sortedBy { secondsOf(it.start) }.mapNotNull { stretch ->
            val start = maxOf(secondsOf(stretch.start), covered)
            val end = secondsOf(stretch.end)
            if (end <= start) return@mapNotNull null
            covered = end
            Triple(maxOf(start, from), minOf(end, to), stretch).takeIf { it.second > it.first }
        }
    }

    // Half a minute rounds up, as in TallyRules.dayTotals.
    private fun minutes(seconds: Long): Int = ((seconds + 30) / 60).toInt()

    // Seconds of a local date-time read as if it were UTC, so a day always has 86,400 of them.
    private fun secondsOf(local: LocalDateTime): Long = local.toEpochSecond(ZoneOffset.UTC)
}
