package com.goalmaker.app.application.planning

import java.time.Instant

/** A stretch of time an [app] (its package) was in front on this phone. Held in memory only (ADR 0013). */
data class UsageInterval(val app: String, val start: Instant, val end: Instant)
