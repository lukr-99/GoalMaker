package com.goalmaker.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goalmaker.app.application.planning.TallyTracker
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Tally's switch and usage access (docs/tally.md) until the Tally place has them (M8-13). Access is
 * the system's to give and take away, so it is read again whenever the screen comes back.
 */
class TallyViewModel(private val tracker: TallyTracker, private val io: CoroutineDispatcher) : ViewModel() {
    private val state = MutableStateFlow(TallyUiState(on = tracker.on.value, granted = tracker.granted()))
    val uiState: StateFlow<TallyUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            tracker.on.collect { on -> state.update { it.copy(on = on) } }
        }
    }

    /** Turns Tally on or off; on with access already granted, the first count runs at once. */
    fun setOn(on: Boolean) {
        tracker.turn(on)
        state.update { it.copy(on = on, granted = tracker.granted()) }
        if (on) count()
    }

    /** Reads usage access again, as the owner may have just granted it or taken it away. Newly granted, it counts. */
    fun checkAccess() {
        val granted = tracker.granted()
        val before = state.value.granted
        state.update { it.copy(granted = granted) }
        if (granted && !before) count()
    }

    private fun count() {
        viewModelScope.launch(io) { tracker.track() }
    }
}
