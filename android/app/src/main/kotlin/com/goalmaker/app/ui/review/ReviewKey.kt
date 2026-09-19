package com.goalmaker.app.ui.review

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The guided review of one period: weekly or monthly, from [periodStart] as an ISO day (docs/reviews.md). */
@Serializable
data class ReviewKey(val kind: String, val periodStart: String) : NavKey
