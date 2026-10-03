package com.goalmaker.app.application.planning

import java.time.LocalDateTime

/**
 * A stretch one app (on Windows one window) was in front, in local time, sorted into a [category]
 * with the rules as they are now. It comes from the device's own record and never syncs (ADR 0013).
 */
data class TallyStretch(val start: LocalDateTime, val end: LocalDateTime, val app: String, val title: String?, val category: String)
