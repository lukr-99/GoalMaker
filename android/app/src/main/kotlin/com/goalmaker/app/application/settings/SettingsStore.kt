package com.goalmaker.app.application.settings

import com.goalmaker.app.application.environment.BackendEnvironment
import com.goalmaker.app.application.update.UpdateMemory
import com.goalmaker.app.domain.planning.QuietHours
import com.goalmaker.app.domain.settings.Appearance
import com.goalmaker.app.domain.settings.BoardView
import com.goalmaker.app.domain.settings.ComposerMode
import com.goalmaker.app.domain.settings.GoalsView
import java.time.Instant
import java.time.LocalTime
import kotlinx.coroutines.flow.StateFlow

/**
 * Device-local settings that are not synced: the appearance, when the planning day starts, quiet
 * hours, the evening reminder, the pinned places, how project boards and goals show, the composer's
 * mode, the app lock, Tally, what the phone knows about a found update and, in dev builds, a backend
 * override.
 */
interface SettingsStore : UpdateMemory {
    val appearance: StateFlow<Appearance>

    fun updateAppearance(change: (Appearance) -> Appearance)

    /** The hour the planning day starts (docs/lists.md), 0 to 6; 4 unless changed. */
    val dayStartHour: StateFlow<Int>

    fun setDayStartHour(hour: Int)

    /** The window that holds ordinary reminders back (docs/reminders.md); off unless set. */
    val quietHours: StateFlow<QuietHours>

    fun setQuietHours(window: QuietHours)

    /** When the evening Plan tomorrow reminder rings (docs/reminders.md); 20:00 unless changed, null when off. */
    val planTomorrowReminder: StateFlow<LocalTime?>

    fun setPlanTomorrowReminder(time: LocalTime?)

    /** When the weekly review reminder rings and on which weekday (1 Monday to 7 Sunday); null when off. */
    val weeklyReviewReminder: StateFlow<LocalTime?>

    val weeklyReviewWeekday: StateFlow<Int>

    fun setWeeklyReviewReminder(time: LocalTime?)

    fun setWeeklyReviewWeekday(weekday: Int)

    /** When the monthly review reminder rings, on the first day of a month; null when off. */
    val monthlyReviewReminder: StateFlow<LocalTime?>

    fun setMonthlyReviewReminder(time: LocalTime?)

    /** When the daily notification for wants that became ready rings (docs/wants.md); 10:00 unless changed, null when off. */
    val wantsReadyReminder: StateFlow<LocalTime?>

    fun setWantsReadyReminder(time: LocalTime?)

    /**
     * The places pinned to the bottom bar, at most four (ADR 0014), in bar order. Read back through
     * `PlaceRules.stored`, so a place that no longer exists is gone and an empty list means the defaults.
     */
    val pins: StateFlow<List<String>>

    fun setPins(pins: List<String>)

    /** How a project's board shows on this phone (docs/projects.md); one column at a time unless changed. */
    val boardView: StateFlow<BoardView>

    fun setBoardView(view: BoardView)

    /** The board columns the list view keeps folded away; Done unless changed. */
    val collapsedColumns: StateFlow<Set<String>>

    fun setCollapsedColumns(columns: Set<String>)

    /** How the Goals screen shows the goals on this phone (docs/goals.md); the ladder unless changed. */
    val goalsView: StateFlow<GoalsView>

    fun setGoalsView(view: GoalsView)

    /** Whether the composer quick-adds or chats (spec, "Quick chat (M7)"); quick-add unless changed. */
    val composerMode: StateFlow<ComposerMode>

    fun setComposerMode(mode: ComposerMode)

    /** Whether this phone asks to be unlocked before it shows the app (docs/sign-in.md); off unless set. */
    val appLock: StateFlow<Boolean>

    fun setAppLock(on: Boolean)

    /** When this device last looked at its reminders, so each one is shown once (docs/reminders.md). */
    fun remindedUntil(): Instant?

    fun setRemindedUntil(instant: Instant)

    /** This install's random id, made the first time it is asked for, that names its Tally rows (docs/tally.md). */
    fun tallyDevice(): String

    /** Whether Tally counts which apps this phone has in front (docs/tally.md); off until the owner turns it on. */
    val tallyOn: StateFlow<Boolean>

    fun setTallyOn(on: Boolean)

    /** How far Tally has read the phone's usage history; null before its first read. */
    fun tallyReadUntil(): Instant?

    fun setTallyReadUntil(instant: Instant?)

    /** When a check for updates last reached the channel (docs/setup/signing-and-releases.md); null before the first. */
    val updatesCheckedAt: StateFlow<Instant?>

    /** The version that check found waiting, or null when it found none; a new start checks again to show it. */
    override fun updateFound(): String?

    fun setUpdateCheck(checkedAt: Instant, found: String?)

    /** Dev builds only: another Supabase project to use from the next app start. */
    fun backendOverride(): BackendEnvironment?

    fun setBackendOverride(environment: BackendEnvironment?)

    /** Dev builds only: sign in and sync from the next app start, instead of keeping everything here. */
    fun devSignIn(): Boolean

    fun setDevSignIn(on: Boolean)
}
