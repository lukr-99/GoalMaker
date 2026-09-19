package com.goalmaker.app.ui.review

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The reviews already written, and the one waiting to be done (docs/reviews.md). */
@Serializable
data object ReviewsKey : NavKey
