package com.goalmaker.app.ui.wants

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goalmaker.app.application.composer.QuickAddLines
import com.goalmaker.app.application.planning.WantCooldowns
import com.goalmaker.app.application.planning.WantDraft
import com.goalmaker.app.application.planning.WantItem
import com.goalmaker.app.application.planning.WantList
import com.goalmaker.app.application.planning.WantRules
import com.goalmaker.app.application.planning.WantState
import com.goalmaker.app.domain.planning.PlanningDay
import com.goalmaker.app.ui.composer.LineOutcome
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Wants place (docs/wants.md, M8-04): wants by where they stand today, their rings, adding one
 * with the cooldown its price gives, deciding and taking a decision back, and the thresholds.
 */
class WantsViewModel(
    private val wants: WantList,
    dayStartHour: StateFlow<Int>,
    private val io: CoroutineDispatcher,
    private val clock: () -> LocalDateTime,
) : ViewModel() {

    /** The filter the owner picked; null until they pick, so Ready leads while anything is ready. */
    private val chosen = MutableStateFlow<WantState?>(null)
    private val undoEvents = MutableSharedFlow<WantUndo>(extraBufferCapacity = 4)

    /** Ticks every minute so a want turns ready when the planning day does. */
    private val minutes = flow {
        while (true) {
            emit(Unit)
            delay(MINUTE)
        }
    }

    val uiState: StateFlow<WantsUiState> = combine(
        wants.watch().flowOn(io),
        wants.watchCooldowns().flowOn(io),
        dayStartHour,
        chosen,
        minutes,
    ) { all, cooldowns, startHour, picked, _ ->
        val today = PlanningDay.of(clock(), startHour)
        val rows = all.mapNotNull { want ->
            val state = WantRules.state(want, today) ?: return@mapNotNull null
            WantRow(
                want = want,
                state = state,
                progress = WantRules.progress(want, today),
                daysLeft = ChronoUnit.DAYS.between(today, want.coolsUntil).toInt().coerceAtLeast(0),
            )
        }
        val counts = WantState.entries.associateWith { state -> rows.count { it.state == state } }
        val filter = picked ?: if ((counts[WantState.READY] ?: 0) > 0) WantState.READY else WantState.COOLING
        WantsUiState(
            loaded = true,
            filter = filter,
            rows = rows.filter { it.state == filter }.let { shown ->
                // Decided wants read newest first; the others by the day they cool.
                if (filter == WantState.DECIDED) shown.sortedByDescending { it.want.decidedAt } else shown
            },
            counts = counts,
            cooldowns = cooldowns,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WantsUiState())

    /** Decisions and deletions the screen offers to undo. */
    val undo: SharedFlow<WantUndo> = undoEvents.asSharedFlow()

    fun show(filter: WantState) {
        chosen.value = filter
    }

    /** The days a want with this price would wait, for the add sheet to say before it is saved. */
    fun cooldownFor(price: Double?, currency: String, picked: Int?): Int =
        WantRules.cooldownDays(price, currency, uiState.value.cooldowns, picked)

    suspend fun add(draft: WantDraft): WantItem? = withContext(io) { wants.add(draft) }

    /**
     * What the bottom bar's line says as a want (docs/composer.md, "Adding on Wants, Habits and
     * Goals"): a picked wait is the want's picked cooldown, and without a price the currency is the
     * owner's, as in the form. Pure and fast, so it runs on every keystroke.
     */
    fun preview(line: String): WantDraft {
        val read = QuickAddLines.readWant(line)
        return WantDraft(
            title = read.title,
            reason = read.reason.orEmpty(),
            price = read.price,
            currency = read.currency ?: uiState.value.cooldowns.currency,
            pickedDays = read.waitDays,
        )
    }

    /**
     * Adds the want the line says when it has a title and a reason; otherwise, or when it can't be
     * saved, the want form opens with what was read.
     */
    suspend fun addLine(line: String): LineOutcome<WantDraft> {
        val draft = preview(line)
        if (draft.title.isBlank() || draft.reason.isBlank()) return LineOutcome.OpenForm(draft)
        return if (add(draft) != null) LineOutcome.Added else LineOutcome.OpenForm(draft)
    }

    suspend fun update(id: String, draft: WantDraft): Boolean = withContext(io) { wants.update(id, draft) }

    fun decide(want: WantItem, decision: String, note: String) {
        viewModelScope.launch(io) {
            if (wants.decide(want.id, decision, note)) {
                undoEvents.tryEmit(WantUndo(if (decision == WantRules.BOUGHT) WantUndo.Kind.BOUGHT else WantUndo.Kind.DROPPED, want.title) {
                    viewModelScope.launch(io) { wants.reopen(want.id) }
                })
            }
        }
    }

    fun reopen(want: WantItem) {
        viewModelScope.launch(io) { wants.reopen(want.id) }
    }

    fun delete(want: WantItem) {
        viewModelScope.launch(io) {
            if (wants.delete(want.id)) {
                undoEvents.tryEmit(WantUndo(WantUndo.Kind.DELETED, want.title) { viewModelScope.launch(io) { wants.restore(want.id) } })
            }
        }
    }

    suspend fun setCooldowns(value: WantCooldowns): Boolean = withContext(io) { wants.setCooldowns(value) }

    private companion object {
        const val MINUTE = 60_000L
    }
}
