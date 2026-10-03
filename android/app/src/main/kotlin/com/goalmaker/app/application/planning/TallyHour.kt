package com.goalmaker.app.application.planning

/** One clock [hour] of a planning day on this device: its seconds and each category's, most first. */
data class TallyHour(val hour: Int, val seconds: Int, val categories: List<TallySeconds>)
