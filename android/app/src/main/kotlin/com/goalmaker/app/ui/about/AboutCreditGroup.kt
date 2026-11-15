package com.goalmaker.app.ui.about

import androidx.annotation.StringRes

/** A heading on the credits card and the credits under it. */
data class AboutCreditGroup(
    @StringRes val title: Int,
    val credits: List<AboutCredit>,
)
