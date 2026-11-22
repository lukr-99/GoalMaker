package com.goalmaker.app.domain.settings

/**
 * How the app looks on this device (docs/design/spec.md, "Settings"). Not synced: each device keeps
 * its own. [themeId] names a theme in contracts/design/themes.json; unknown ids fall back to the
 * default theme.
 */
data class Appearance(
    val themeId: String?,
    val mode: ThemeMode,
    val pureBlack: Boolean,
    val reduceMotion: ReduceMotion,
    val completionSound: Boolean,
    /** Habits show as short rows, without the progress, the week's dots and the map. */
    val compactHabits: Boolean = false,
) {
    companion object {
        /** A fresh install: the default theme, following the system's light or dark. */
        val DEFAULT = Appearance(
            themeId = null,
            mode = ThemeMode.SYSTEM,
            pureBlack = false,
            reduceMotion = ReduceMotion.SYSTEM,
            completionSound = false,
            compactHabits = false,
        )
    }
}
