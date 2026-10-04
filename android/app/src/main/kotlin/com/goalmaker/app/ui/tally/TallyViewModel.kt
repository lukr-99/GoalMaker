package com.goalmaker.app.ui.tally

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goalmaker.app.application.planning.ProjectItem
import com.goalmaker.app.application.planning.ProjectList
import com.goalmaker.app.application.planning.TallyBreakdown
import com.goalmaker.app.application.planning.TallyCategory
import com.goalmaker.app.application.planning.TallyDay
import com.goalmaker.app.application.planning.TallyFilter
import com.goalmaker.app.application.planning.TallyList
import com.goalmaker.app.application.planning.TallyMinutes
import com.goalmaker.app.application.planning.TallyRule
import com.goalmaker.app.application.planning.TallyRules
import com.goalmaker.app.application.planning.TallyStretch
import com.goalmaker.app.application.planning.TallyTracker
import com.goalmaker.app.domain.planning.PlanningDay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Tally place (docs/tally.md, M8-13): the switch and usage access at the top, then where this
 * week's time went on every device, filtered by kind of device and category, a day of it closer up
 * (by hour, and by app from this phone's own history, which never syncs), and the owner's own
 * categories and rules. Access is the system's to give and take away, so it is read again whenever
 * the screen comes back.
 */
class TallyViewModel(
    private val tracker: TallyTracker,
    private val tally: TallyList,
    projects: ProjectList,
    private val defaults: List<TallyCategory>,
    dayStartHour: StateFlow<Int>,
    private val io: CoroutineDispatcher,
    private val clock: () -> LocalDateTime,
) : ViewModel() {
    private val accessState = MutableStateFlow(TallyAccess(on = tracker.on.value, granted = tracker.granted()))
    private val filter = MutableStateFlow(TallyFilter())
    private val view = MutableStateFlow(View())

    /** The switch and whether the usage access card shows. */
    val access: StateFlow<TallyAccess> = accessState.asStateFlow()

    /** Ticks every minute so today moves on when the planning day does. */
    private val minutes = flow {
        while (true) {
            emit(Unit)
            delay(MINUTE)
        }
    }

    private val week = combine(tally.watch(), projects.watch(), dayStartHour, minutes, accessState) { _, projectData, startHour, _, access ->
        val today = PlanningDay.of(clock(), startHour)
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        // This phone's own history, read fresh each time and kept only in memory (ADR 0013).
        val counting = access.on && access.granted
        val local = if (counting) tracker.stretches(monday, today) else emptyList()
        val names = local.map(TallyStretch::app).distinct().mapNotNull { app -> tracker.appName(app)?.let { app.lowercase(Locale.ROOT) to it } }.toMap()
        Week(today, startHour, tally.totals(monday, today), tally.categories(), tally.rules(), projectData.projects, counting, local, names)
    }.flowOn(io)

    val uiState: StateFlow<TallyUiState> = combine(week, filter, view) { data, shown, look ->
        val categories = TallyBoard.lookup(defaults, data.own)
        val monday = data.today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val day = look.day?.takeIf { !it.isBefore(monday) && !it.isAfter(data.today) } ?: data.today
        // The category chip narrows this phone's hours and apps too.
        val local = data.local.filter { shown.category == null || it.category == shown.category }
        val apps = when (look.scope) {
            TallyAppScope.DAY -> TallyBreakdown.apps(local, day, day, data.startHour)
            TallyAppScope.WEEK -> TallyBreakdown.apps(local, monday, data.today, data.startHour)
        }
        val chips = TallyBoard.chips(data.rows, shown.kind, categories).let { chips ->
            // The category picked stays a chip, so it can be let go, even with no time on this device.
            val picked = shown.category
            if (picked == null || chips.any { it.category == picked }) chips
            else chips + TallyBoard.slices(listOf(TallyMinutes(picked, 0)), categories)
        }
        TallyUiState(
            loaded = true,
            filter = shown,
            day = TallyBoard.day(data.rows, day, shown, categories),
            isToday = day == data.today,
            week = TallyBoard.week(data.rows, data.today, shown, categories),
            weekSlices = TallyBoard.bar(data.today, data.rows.filter { TallyRules.keeps(it, shown) }, categories).slices,
            projects = TallyBoard.projects(data.rows, shown, data.projects),
            chips = chips,
            categories = data.own + defaults,
            own = data.own,
            rules = data.rules,
            counting = data.counting,
            hours = TallyBoard.hours(TallyBreakdown.hours(local, day, data.startHour), categories),
            appScope = look.scope,
            apps = TallyBoard.apps(apps, categories, data.names),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TallyUiState())

    init {
        viewModelScope.launch {
            tracker.on.collect { on -> accessState.update { it.copy(on = on) } }
        }
    }

    /** Turns Tally on or off; on with access already granted, the first count runs at once. */
    fun setOn(on: Boolean) {
        tracker.turn(on)
        accessState.update { it.copy(on = on, granted = tracker.granted()) }
        if (on) count()
    }

    /** Reads usage access again, as the owner may have just granted it or taken it away. Newly granted, it counts. */
    fun checkAccess() {
        val granted = tracker.granted()
        val before = accessState.value.granted
        accessState.update { it.copy(granted = granted) }
        if (granted && !before) count()
    }

    /** Shows one kind of device (`phone` or `pc`), or both again when that kind is picked a second time. */
    fun showKind(kind: String) = filter.update { it.copy(kind = if (it.kind == kind) null else kind) }

    /** Shows one category, or every category again when it is picked a second time. */
    fun showCategory(category: String) = filter.update { it.copy(category = if (it.category == category) null else category) }

    /** Shows one day of the week closer up, or today again when that day is picked a second time. */
    fun showDay(day: LocalDate) = view.update { it.copy(day = if (it.day == day) null else day) }

    /** Shows this phone's apps for the day shown or for the whole week. */
    fun showApps(scope: TallyAppScope) = view.update { it.copy(scope = scope) }

    /** A new rule that puts one of this phone's apps into a category, for the rule sheet to start from. */
    fun ruleFor(app: String, category: String): TallyRule = TallyRule(TallyRules.APP, app, TallyRules.ANDROID, category)

    suspend fun addCategory(name: String, color: String, emoji: String): TallyCategory? =
        withContext(io) { tally.addCategory(name, color, emoji) }

    suspend fun updateCategory(id: String, name: String, color: String, emoji: String): Boolean =
        withContext(io) { tally.updateCategory(id, name, color, emoji) }

    /** Deletes one of the owner's categories; time already sorted into it shows as a removed category. */
    fun deleteCategory(id: String) {
        viewModelScope.launch(io) { tally.deleteCategory(id) }
    }

    /** Adds a rule; this phone counts again at once, so the day it last read is sorted by it now. */
    suspend fun addRule(rule: TallyRule): TallyRule? = withContext(io) { tally.addRule(rule)?.also { tracker.track() } }

    suspend fun updateRule(id: String, rule: TallyRule): Boolean = withContext(io) { tally.updateRule(id, rule).also { if (it) tracker.track() } }

    fun deleteRule(id: String) {
        viewModelScope.launch(io) { if (tally.deleteRule(id)) tracker.track() }
    }

    private fun count() {
        viewModelScope.launch(io) { tracker.track() }
    }

    private data class Week(
        val today: LocalDate,
        val startHour: Int,
        val rows: List<TallyDay>,
        val own: List<TallyCategory>,
        val rules: List<TallyRule>,
        val projects: List<ProjectItem>,
        val counting: Boolean,
        val local: List<TallyStretch>,
        /** The apps' names this phone knows, by package in lower case. */
        val names: Map<String, String>,
    )

    /** What the owner picked to look at closer: a day of the week (null for today) and the apps' scope. */
    private data class View(val day: LocalDate? = null, val scope: TallyAppScope = TallyAppScope.DAY)

    private companion object {
        const val MINUTE = 60_000L
    }
}
