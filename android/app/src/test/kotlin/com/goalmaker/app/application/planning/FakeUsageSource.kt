package com.goalmaker.app.application.planning

import java.time.Instant

/** A phone's usage history in memory: the stretches it holds, whether access is granted, and what was asked. */
class FakeUsageSource : UsageSource {
    val stretches = mutableListOf<UsageInterval>()
    var granted = true
    val reads = mutableListOf<Pair<Instant, Instant>>()
    val names = mutableMapOf<String, String>()

    fun add(app: String, start: String, end: String) {
        stretches += UsageInterval(app, Instant.parse(start), Instant.parse(end))
    }

    override fun granted(): Boolean = granted

    override fun foreground(from: Instant, to: Instant): List<UsageInterval>? {
        if (!granted) return null
        reads += from to to
        return stretches
            .filter { it.end.isAfter(from) && it.start.isBefore(to) }
            .map { it.copy(start = maxOf(it.start, from), end = minOf(it.end, to)) }
    }

    override fun appName(app: String): String? = names[app]
}
