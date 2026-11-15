package com.goalmaker.app.ui.about

import androidx.annotation.StringRes

/** One thing GoalMaker ships that someone made: its name, what it does here and its licence. */
data class AboutCredit(
    val name: String,
    @StringRes val role: Int,
    val licence: String,
)
