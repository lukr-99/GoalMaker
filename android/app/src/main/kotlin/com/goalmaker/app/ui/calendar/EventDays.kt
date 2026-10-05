package com.goalmaker.app.ui.calendar

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.goalmaker.app.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * An event's days in words: "12 October" for one day, "12 to 15 October" inside a month, "30 September
 * to 2 October" across a month end, and the years too when it crosses one.
 */
@Composable
fun eventDays(startsOn: LocalDate, endsOn: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    val dayMonth = DateTimeFormatter.ofPattern("d MMMM", locale)
    if (startsOn == endsOn) return dayMonth.format(startsOn)
    val (first, last) = when {
        startsOn.year != endsOn.year -> DateTimeFormatter.ofPattern("d MMMM yyyy", locale).let { it.format(startsOn) to it.format(endsOn) }
        startsOn.month != endsOn.month -> dayMonth.format(startsOn) to dayMonth.format(endsOn)
        else -> DateTimeFormatter.ofPattern("d", locale).format(startsOn) to dayMonth.format(endsOn)
    }
    return stringResource(R.string.calendar_event_range, first, last)
}
