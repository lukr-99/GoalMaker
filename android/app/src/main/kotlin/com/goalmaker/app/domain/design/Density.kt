package com.goalmaker.app.domain.design

/** Spacing for this device, in dp (the phone is airy; spacing never comes from the theme). */
data class Density(
    val rowMinHeight: Int,
    val rowGap: Int,
    val pagePadding: Int,
    val cardPadding: Int,
)
