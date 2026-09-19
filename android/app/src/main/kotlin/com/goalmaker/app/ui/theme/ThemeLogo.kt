package com.goalmaker.app.ui.theme

import com.goalmaker.app.domain.design.LogoColors
import com.goalmaker.app.domain.design.LogoMark

/** The GoalMaker mark and the current theme's colors for it, as [AppTheme.logo] hands them out. */
data class ThemeLogo(
    val mark: LogoMark,
    val colors: LogoColors,
)
