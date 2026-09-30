package com.goalmaker.app.application.planning

import com.goalmaker.app.domain.planning.NameBasedUuid
import com.goalmaker.app.domain.planning.PlanningDay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale

/**
 * Tally's rules (docs/tally.md, ADR 0013), pinned by contracts/vectors/tally.json, which the Windows
 * app and the connector run too: which category and project a moment of foreground time belongs to,
 * when the clock stops for idle, and how a device's intervals become the daily totals that sync.
 */
object TallyRules {
    const val APP = "app"
    const val TITLE = "title"
    const val FOLDER = "folder"
    val MATCHES = setOf(APP, TITLE, FOLDER)

    const val ANDROID = "android"
    const val WINDOWS = "windows"
    const val ANY = "any"
    val PLATFORMS = setOf(ANDROID, WINDOWS, ANY)

    /** What a tally day's device is. */
    const val PHONE = "phone"
    const val PC = "pc"

    /** Where time goes that no rule claims. */
    const val OTHER = "other"

    /** The clock stops after this long without input, unless the window is in the [VIDEO] category. */
    const val IDLE_SECONDS = 300L
    const val VIDEO = "video"

    /** A day holds at most this many minutes, per device and category. */
    const val MAX_MINUTES = 1440

    private const val NAMESPACE = "b8c61b22-5e0c-4f0a-9d1e-6f4a2c7e3b91"
    private const val VS_CODE = " - Visual Studio Code"
    private const val VISUAL_STUDIO = " - Microsoft Visual Studio"
    private val RUNNING = Regex("""\s*\([^)]*\)\s*$""")
    private val TRAILING_SLASHES = Regex("""[\\/]+$""")
    private val SLASH = Regex("""[\\/]""")

    /**
     * The folder an editor's window title names: Visual Studio Code's workspace (the part before
     * " - Visual Studio Code"), Android Studio's project (the part before the first " – "), and Visual
     * Studio's solution (the part before " - Microsoft Visual Studio", without "(Running)" and the like).
     * Null for anything that isn't one of these editors.
     */
    fun editorFolder(app: String, title: String?): String? {
        val text = title.orEmpty().trim()
        if (text.isEmpty()) return null
        val folder = when (app.trim().lowercase(Locale.ROOT)) {
            "code.exe" -> if (text.endsWith(VS_CODE)) text.dropLast(VS_CODE.length).split(" - ").last() else null
            "studio64.exe" -> text.split(" – ").first()
            "devenv.exe" -> text.indexOf(VISUAL_STUDIO).takeIf { it > 0 }?.let { end -> text.substring(0, end).replaceFirst(RUNNING, "") }
            else -> null
        }
        return folder?.trimStart { it == '●' || it.isWhitespace() }?.trim()?.takeIf(String::isNotEmpty)
    }

    /**
     * The project an editor's [folder] belongs to: the one project whose local folder has that name, from
     * [folders] (a project's id to its local folder). None when no project, or more than one, has it.
     */
    fun projectFor(folder: String?, folders: Map<String, String?>): String? {
        val name = folderName(folder) ?: return null
        return folders.filterValues { folderName(it) == name }.keys.singleOrNull()
    }

    /**
     * Where a sample goes: the first of the owner's rules that matches, then the first default, then
     * [OTHER]. A rule's own project wins; otherwise, on Windows, the editor's folder names the project.
     */
    fun sortSample(
        sample: TallySample,
        own: List<TallyRule>,
        defaults: List<TallyRule>,
        folders: Map<String, String?> = emptyMap(),
    ): TallySort {
        val folder = if (sample.platform == WINDOWS) editorFolder(sample.app, sample.title) else null
        val rule = (own + defaults).firstOrNull { matches(it, sample, folder) }
        // The phone never links time to a project, not even through a rule (docs/tally.md).
        val project = if (sample.platform == WINDOWS) rule?.project ?: projectFor(folder, folders) else null
        return TallySort(rule?.category ?: OTHER, project)
    }

    /** Whether the clock runs: not while locked or asleep, and not after five idle minutes unless it is video. */
    fun counts(secondsSinceInput: Long, category: String, locked: Boolean, asleep: Boolean): Boolean {
        if (locked || asleep) return false
        return category == VIDEO || secondsSinceInput < IDLE_SECONDS
    }

    /**
     * A device's intervals as planning-day totals: each interval is cut at the hour the day starts,
     * time two intervals share is counted once (for the one that started first), and each day, category
     * and project gets its seconds rounded to the nearest minute, at most a whole day. Rows with no
     * minutes are left out; the rest are ordered by day, category and project (none first).
     */
    fun dayTotals(intervals: List<TallyInterval>, startHour: Int): List<TallyTotal> {
        val seconds = LinkedHashMap<Triple<LocalDate, String, String?>, Long>()
        var covered = Long.MIN_VALUE
        for (interval in intervals.sortedBy { secondsOf(it.start) }) {
            var from = maxOf(secondsOf(interval.start), covered)
            val to = secondsOf(interval.end)
            if (to <= from) continue
            covered = to
            while (from < to) {
                val day = PlanningDay.of(LocalDateTime.ofEpochSecond(from, 0, ZoneOffset.UTC), startHour)
                val until = minOf(to, secondsOf(day.plusDays(1).atTime(startHour, 0)))
                val key = Triple(day, interval.category, interval.project)
                seconds[key] = (seconds[key] ?: 0L) + (until - from)
                from = until
            }
        }
        return seconds
            // Half a minute rounds up, as JavaScript's Math.round does for the connector.
            .map { (key, value) -> TallyTotal(key.first, key.second, key.third, minOf(MAX_MINUTES.toLong(), (value + 30) / 60).toInt()) }
            .filter { it.minutes > 0 }
            .sortedWith(compareBy<TallyTotal> { it.day }.thenBy { it.category }.thenBy { it.project.orEmpty() })
    }

    /** The id every device gives its row for a day, category and project, so rewriting a day replaces it. */
    fun dayId(owner: String, day: LocalDate, device: String, category: String, project: String?): String = NameBasedUuid.of(
        NAMESPACE,
        "tally/${owner.lowercase(Locale.ROOT)}/$day/${device.lowercase(Locale.ROOT)}/$category/${project ?: "-"}",
    )

    private fun matches(rule: TallyRule, sample: TallySample, folder: String?): Boolean {
        if (rule.platform != ANY && rule.platform != sample.platform) return false
        val pattern = rule.pattern.trim().lowercase(Locale.ROOT)
        if (pattern.isEmpty()) return false
        return when (rule.match) {
            APP -> sample.app.trim().lowercase(Locale.ROOT) == pattern
            TITLE -> sample.platform == WINDOWS && sample.title.orEmpty().lowercase(Locale.ROOT).contains(pattern)
            FOLDER -> folderName(folder)?.let { it == folderName(pattern) } ?: false
            else -> false
        }
    }

    /** The last part of a path, or the whole of a bare name, ignoring case. */
    private fun folderName(path: String?): String? =
        path.orEmpty().trim().replace(TRAILING_SLASHES, "").split(SLASH).last().trim().lowercase(Locale.ROOT).takeIf(String::isNotEmpty)

    // Seconds of a local date-time read as if it were UTC, so a day always has 86,400 of them.
    private fun secondsOf(local: LocalDateTime): Long = local.toEpochSecond(ZoneOffset.UTC)
}
