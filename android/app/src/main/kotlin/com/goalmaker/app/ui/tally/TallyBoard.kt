package com.goalmaker.app.ui.tally

import com.goalmaker.app.application.planning.ProjectItem
import com.goalmaker.app.application.planning.TallyCategory
import com.goalmaker.app.application.planning.TallyCategoryApps
import com.goalmaker.app.application.planning.TallyDay
import com.goalmaker.app.application.planning.TallyFilter
import com.goalmaker.app.application.planning.TallyHour
import com.goalmaker.app.application.planning.TallyMinutes
import com.goalmaker.app.application.planning.TallyRules
import com.goalmaker.app.application.planning.TallyWeek
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Tally's rows as the bars the place, the stats and the review draw (docs/tally.md): minutes by
 * category with each category's name and color, the shipped ones' or the owner's own.
 */
object TallyBoard {
    /** The color a category no longer known takes. */
    private const val UNKNOWN_COLOR = "slate"

    /** How many weeks the stats block shows. */
    const val STATS_WEEKS = 12

    /** Every category by id: the shipped ones and the owner's own. */
    fun lookup(defaults: List<TallyCategory>, own: List<TallyCategory>): Map<String, TallyCategory> =
        (defaults + own).associateBy(TallyCategory::id)

    /** The categories' minutes as slices, in the order given. */
    fun slices(minutes: List<TallyMinutes>, categories: Map<String, TallyCategory>): List<TallySlice> = minutes.map { part ->
        val category = categories[part.category]
        TallySlice(part.category, category?.name.orEmpty(), category?.color ?: UNKNOWN_COLOR, part.minutes)
    }

    /** One day's bar: the minutes [filter] keeps, by category. */
    fun day(rows: List<TallyDay>, day: LocalDate, filter: TallyFilter, categories: Map<String, TallyCategory>): TallyBar =
        bar(day, rows.filter { it.day == day && TallyRules.keeps(it, filter) }, categories)

    /** A bar per day of the week holding [today], Monday first; the days still ahead are empty. */
    fun week(rows: List<TallyDay>, today: LocalDate, filter: TallyFilter, categories: Map<String, TallyCategory>): List<TallyBar> {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return (0L..6L).map { offset -> day(rows, monday.plusDays(offset), filter, categories) }
    }

    /**
     * The minutes linked to each project, most first. Only the PC links time to a project, so the phone
     * filter leaves nothing; a category filter keeps its own time.
     */
    fun projects(rows: List<TallyDay>, filter: TallyFilter, projects: List<ProjectItem>): List<TallyProjectTime> = rows
        .filter { it.project != null && TallyRules.keeps(it, filter) }
        .groupingBy { it.project!! }
        .fold(0) { sum, row -> sum + row.minutes }
        .filterValues { it > 0 }
        .map { (id, minutes) -> TallyProjectTime(id, projects.firstOrNull { it.id == id }?.name.orEmpty(), minutes) }
        .sortedWith(compareByDescending<TallyProjectTime> { it.minutes }.thenBy { it.name })

    /** A chip for each category with time on the kind of device picked, most first. */
    fun chips(rows: List<TallyDay>, kind: String?, categories: Map<String, TallyCategory>): List<TallySlice> =
        slices(TallyRules.byCategory(rows.filter { TallyRules.keeps(it, TallyFilter(kind = kind)) }), categories)

    /** The stats block's weeks as bars, oldest first. */
    fun weeks(weeks: List<TallyWeek>, categories: Map<String, TallyCategory>): List<TallyBar> =
        weeks.map { week -> TallyBar(week.start, week.minutes, slices(week.categories, categories)) }

    /** The rows' minutes as one bar for [day], by category. */
    fun bar(day: LocalDate, rows: List<TallyDay>, categories: Map<String, TallyCategory>): TallyBar =
        TallyBar(day, rows.sumOf(TallyDay::minutes), slices(TallyRules.byCategory(rows), categories))

    /** A day's hours on this phone as the day chart's bars, each category in its color. */
    fun hours(hours: List<TallyHour>, categories: Map<String, TallyCategory>): List<TallyHourBar> = hours.map { hour ->
        TallyHourBar(
            hour.hour,
            hour.seconds,
            hour.categories.map { part ->
                val category = categories[part.category]
                TallyHourPart(part.category, category?.name.orEmpty(), category?.color ?: UNKNOWN_COLOR, part.seconds)
            },
        )
    }

    /** This phone's apps by category, each app by the name the phone knows it by ([names], by package in lower case). */
    fun apps(groups: List<TallyCategoryApps>, categories: Map<String, TallyCategory>, names: Map<String, String>): List<TallyAppGroup> =
        groups.map { group ->
            val category = categories[group.category]
            TallyAppGroup(
                category = group.category,
                name = category?.name.orEmpty(),
                color = category?.color ?: UNKNOWN_COLOR,
                emoji = category?.emoji,
                minutes = group.minutes,
                apps = group.apps.map { app -> TallyAppRow(app.app, names[app.app] ?: app.app, app.minutes, app.windows) },
            )
        }
}
