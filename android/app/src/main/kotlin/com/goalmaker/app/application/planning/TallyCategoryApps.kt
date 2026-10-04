package com.goalmaker.app.application.planning

/** One category's minutes on this device and the apps that made them up, most first. */
data class TallyCategoryApps(val category: String, val minutes: Int, val apps: List<TallyAppTime>)
