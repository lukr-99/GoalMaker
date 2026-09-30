package com.goalmaker.app.ui.tally

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goalmaker.app.application.planning.ProjectItem
import com.goalmaker.app.application.planning.ProjectList
import com.goalmaker.app.application.planning.TallyCategory
import com.goalmaker.app.application.planning.TallyDay
import com.goalmaker.app.application.planning.TallyFilter
import com.goalmaker.app.application.planning.TallyList
import com.goalmaker.app.application.planning.TallyMinutes
import com.goalmaker.app.application.planning.TallyRule
import com.goalmaker.app.application.planning.TallyRules
import com.goalmaker.app.application.planning.TallyTracker
import com.goalmaker.app.domain.planning.PlanningDay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
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
 * week's time went on every device, filtered by kind of device and category, and the owner's own
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

    /** The switch and whether the usage access card shows. */
    val access: StateFlow<TallyAccess> = accessState.asStateFlow()

    /** Ticks every minute so today moves on when the planning day does. */
    private val minutes = flow {
        while (true) {
            emit(Unit)
            delay(MINUTE)
        }
    }

    private val week = combine(tally.watch(), projects.watch(), dayStartHour, minutes) { _, projectData, startHour, _ ->
        val today = PlanningDay.of(clock(), startHour)
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        Week(today, tally.totals(monday, today), tally.categories(), tally.rules(), projectData.projects)
    }.flowOn(io)

    val uiState: StateFlow<TallyUiState> = combine(week, filter) { data, shown ->
        val categories = TallyBoard.lookup(defaults, data.own)
        val chips = TallyBoard.chips(data.rows, shown.kind, categories).let { chips ->
            // The category picked stays a chip, so it can be let go, even with no time on this device.
            val picked = shown.category
            if (picked == null || chips.any { it.category == picked }) chips
            else chips + TallyBoard.slices(listOf(TallyMinutes(picked, 0)), categories)
        }
        TallyUiState(
            loaded = true,
            filter = shown,
            today = TallyBoard.day(data.rows, data.today, shown, categories),
            week = TallyBoard.week(data.rows, data.today, shown, categories),
            weekSlices = TallyBoard.bar(data.today, data.rows.filter { TallyRules.keeps(it, shown) }, categories).slices,
            projects = TallyBoard.projects(data.rows, shown, data.projects),
            chips = chips,
            categories = data.own + defaults,
            own = data.own,
            rules = data.rules,
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

    suspend fun addCategory(name: String, color: String, emoji: String): TallyCategory? =
        withContext(io) { tally.addCategory(name, color, emoji) }

    suspend fun updateCategory(id: String, name: String, color: String, emoji: String): Boolean =
        withContext(io) { tally.updateCategory(id, name, color, emoji) }

    /** Deletes one of the owner's categories; time already sorted into it shows as a removed category. */
    fun deleteCategory(id: String) {
        viewModelScope.launch(io) { tally.deleteCategory(id) }
    }

    suspend fun addRule(rule: TallyRule): TallyRule? = withContext(io) { tally.addRule(rule) }

    suspend fun updateRule(id: String, rule: TallyRule): Boolean = withContext(io) { tally.updateRule(id, rule) }

    fun deleteRule(id: String) {
        viewModelScope.launch(io) { tally.deleteRule(id) }
    }

    private fun count() {
        viewModelScope.launch(io) { tracker.track() }
    }

    private data class Week(
        val today: LocalDate,
        val rows: List<TallyDay>,
        val own: List<TallyCategory>,
        val rules: List<TallyRule>,
        val projects: List<ProjectItem>,
    )

    private companion object {
        const val MINUTE = 60_000L
    }
}
