package com.goalmaker.app.domain.design

/** A theme's three text styles (docs/design/spec.md, "Type"). */
data class ThemeTypography(
    val heading: TypeStyle,
    val body: BodyStyle,
    val number: TypeStyle,
)
