package com.goalmaker.app.ui.lifegoals

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.TimeLeft
import com.goalmaker.app.application.planning.TimeLeftUnit

/** A life goal's time left in words: "10 years left", "8 months left", "Today", "Past its date". */
@Composable
fun timeLeftText(left: TimeLeft): String = when (left.unit) {
    TimeLeftUnit.YEARS -> pluralStringResource(R.plurals.life_goals_years_left, left.count, left.count)
    TimeLeftUnit.MONTHS -> pluralStringResource(R.plurals.life_goals_months_left, left.count, left.count)
    TimeLeftUnit.DAYS -> pluralStringResource(R.plurals.life_goals_days_left, left.count, left.count)
    TimeLeftUnit.TODAY -> stringResource(R.string.life_goals_today)
    TimeLeftUnit.PAST -> stringResource(R.string.life_goals_past)
}
