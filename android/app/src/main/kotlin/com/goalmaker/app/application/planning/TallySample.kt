package com.goalmaker.app.application.planning

/** What was in front on a device: an [app] (a package or an executable), and on Windows its window [title]. */
data class TallySample(val platform: String, val app: String, val title: String? = null)
