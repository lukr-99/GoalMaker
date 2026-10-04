package com.goalmaker.app.application.planning

/** How far a life goal's by date is: "10 years left", "Today", "Past its date". */
data class TimeLeft(val unit: TimeLeftUnit, val count: Int)
