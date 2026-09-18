package com.goalmaker.app.domain.sync

import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * The sync decisions both apps make the same way (docs/sync.md), pinned by
 * contracts/vectors/sync-merge.json.
 */
object SyncRules {
    const val OVERLAP_SECONDS = 60L
    const val FULL_RESYNC_DAYS = 80L

    private val shape = Regex("^\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,9})?(Z|[+-]\\d{2}(:?\\d{2})?)$")
    private val output = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneOffset.UTC)

    /** Merge one pulled row. A pending local change wins, except against a tombstone (delete wins). */
    fun merge(local: RowVersion?, pending: Boolean, remote: RowVersion): MergeDecision = when {
        pending && remote.deleted -> MergeDecision(takeRemote = true, dropPending = true)
        pending -> MergeDecision(takeRemote = false, dropPending = false)
        local == null -> MergeDecision(takeRemote = true, dropPending = false)
        else -> MergeDecision(takeRemote = remote.updatedAt > local.updatedAt, dropPending = false)
    }

    /** A table starts over when it never synced or its watermark is more than 80 days old. */
    fun needsFullResync(watermark: String?, now: Instant): Boolean {
        val last = watermark?.let(::parse) ?: return true
        return Duration.between(last, now) > Duration.ofDays(FULL_RESYNC_DAYS)
    }

    /** Where a pull starts: 60 seconds before the watermark, or from the beginning. */
    fun pullFrom(watermark: String?): String? =
        watermark?.let(::parse)?.minusSeconds(OVERLAP_SECONDS)?.let(output::format)

    /** Server timestamps as UTC text with 6 fractional digits, so text order is time order. */
    fun normalizeTimestamp(text: String): String? = parse(text)?.let(output::format)

    fun format(instant: Instant): String = output.format(instant)

    /** A stored timestamp read back as an instant, or null when the text isn't one. */
    fun instantOf(text: String): Instant? = parse(text)

    private fun parse(text: String): Instant? {
        if (!shape.matches(text)) return null
        var iso = text.replace(' ', 'T')
        // Postgres writes offsets as +00 or +0000; java.time wants +00:00.
        iso = iso.replace(Regex("([+-]\\d{2})$"), "$1:00").replace(Regex("([+-]\\d{2})(\\d{2})$"), "$1:$2")
        return try {
            OffsetDateTime.parse(iso).toInstant()
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
