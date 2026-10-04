package com.goalmaker.app.application.planning

/** One app's minutes in a category (its package or executable in lower case), and its sites or folders. */
data class TallyAppTime(val app: String, val minutes: Int, val windows: List<TallyWindowTime>)
