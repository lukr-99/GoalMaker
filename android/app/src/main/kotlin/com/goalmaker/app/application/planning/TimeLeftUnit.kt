package com.goalmaker.app.application.planning

/** What a [TimeLeft] counts, as contracts/vectors/life-goals.json names it. */
enum class TimeLeftUnit(val key: String) {
    YEARS("years"),
    MONTHS("months"),
    DAYS("days"),
    TODAY("today"),
    PAST("past"),
}
