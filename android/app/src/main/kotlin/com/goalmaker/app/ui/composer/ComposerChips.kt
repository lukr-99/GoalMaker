package com.goalmaker.app.ui.composer

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.AreaItem
import com.goalmaker.app.domain.composer.ComposerDraft
import com.goalmaker.app.domain.composer.ComposerSpan
import com.goalmaker.app.domain.composer.SpanKind
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** The preview chips for [draft], in the order the item reads: when, how often, where, tags, flags. */
@Composable
fun composerChips(line: String, draft: ComposerDraft, today: LocalDate, areas: List<AreaItem>, tagNames: List<String>): List<ComposerChip> {
    val locale = LocalConfiguration.current.locales[0]
    val spans = draft.spans
    fun of(kind: SpanKind) = spans.filter { it.kind == kind }
    val newNote = stringResource(R.string.composer_new)
    val laterNote = stringResource(R.string.composer_later)
    val chips = mutableListOf<ComposerChip>()

    draft.command?.let { command ->
        val note = stringResource(if (command.known) R.string.composer_soon else R.string.composer_unknown_command)
        chips += ComposerChip(SpanKind.COMMAND, "/${command.name}", note, muted = true, areaColorId = null, spans = of(SpanKind.COMMAND))
        return chips
    }
    draft.plannedDate?.let { date ->
        val label = when (date) {
            today -> stringResource(R.string.composer_date_today)
            today.plusDays(1) -> stringResource(R.string.composer_date_tomorrow)
            else -> DateTimeFormatter.ofPattern(if (date.year == today.year) "EEE d MMM" else "EEE d MMM yyyy", locale).format(date)
        }
        chips += ComposerChip(SpanKind.DATE, label, null, false, null, of(SpanKind.DATE))
    }
    draft.plannedTime?.let { time ->
        chips += ComposerChip(SpanKind.TIME, DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(time), null, false, null, of(SpanKind.TIME))
    }
    draft.repeat?.let { rule ->
        chips += ComposerChip(SpanKind.REPEAT, describeRepeat(rule, locale), null, false, null, of(SpanKind.REPEAT))
    }
    draft.area?.let { name ->
        val existing = areas.firstOrNull { it.name.trim().lowercase(Locale.ROOT) == name.trim().lowercase(Locale.ROOT) }
        chips += ComposerChip(SpanKind.AREA, existing?.name ?: name, if (existing == null) newNote else null, false, existing?.colorId, of(SpanKind.AREA))
    }
    val known = tagNames.map { it.lowercase(Locale.ROOT) }.toSet()
    draft.tags.forEach { tag ->
        val tagSpans = of(SpanKind.TAG).filter { span ->
            line.substring(span.start + 1, span.end).trimEnd { it in ".,;:!?" }.lowercase(Locale.ROOT) == tag.lowercase(Locale.ROOT)
        }
        chips += ComposerChip(SpanKind.TAG, "#$tag", if (tag.lowercase(Locale.ROOT) in known) null else newNote, false, null, tagSpans)
    }
    if (draft.topPriority) {
        chips += ComposerChip(SpanKind.PRIORITY, stringResource(R.string.composer_priority), null, false, null, of(SpanKind.PRIORITY))
    }
    draft.project?.let { name ->
        chips += ComposerChip(SpanKind.PROJECT, "+$name", laterNote, true, null, of(SpanKind.PROJECT))
    }
    if (draft.idea) {
        chips += ComposerChip(SpanKind.IDEA, stringResource(R.string.composer_idea), laterNote, true, null, of(SpanKind.IDEA))
    }
    return chips
}

/** The line without the given parts, spaces tidied. */
fun removeParts(line: String, spans: List<ComposerSpan>): String {
    val text = StringBuilder(line)
    spans.sortedByDescending { it.start }.forEach { text.delete(it.start, it.end) }
    return text.toString().replace(WHITESPACE, " ").trim()
}

private val WHITESPACE = Regex("\\s+")

@Composable
private fun describeRepeat(rule: String, locale: Locale): String {
    val parts = rule.split(';').associate { it.substringBefore('=') to it.substringAfter('=') }
    val interval = parts["INTERVAL"]?.toIntOrNull() ?: 1
    return when (parts["FREQ"]) {
        "DAILY" -> if (interval > 1) stringResource(R.string.repeat_every_n_days, interval) else stringResource(R.string.repeat_daily)
        "WEEKLY" -> {
            val codes = parts["BYDAY"].orEmpty().split(',')
            val days = codes.mapNotNull { code -> DayOfWeek.entries.firstOrNull { it.name.startsWith(code) } }
            val names = days.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
            when {
                interval > 1 -> stringResource(R.string.repeat_every_n_weeks_on, interval, names)
                days.toSet() == WORK_WEEK -> stringResource(R.string.repeat_weekdays)
                days.toSet() == WEEKEND -> stringResource(R.string.repeat_weekend)
                days.size == 1 -> stringResource(R.string.repeat_every_day_name, days.single().getDisplayName(TextStyle.FULL, locale))
                else -> stringResource(R.string.repeat_every_day_name, names)
            }
        }
        "MONTHLY" -> {
            val day = parts["BYMONTHDAY"]?.toIntOrNull() ?: 1
            if (interval > 1) stringResource(R.string.repeat_every_n_months_on, interval, day) else stringResource(R.string.repeat_monthly_on, day)
        }
        else -> rule
    }
}

private val WORK_WEEK = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
