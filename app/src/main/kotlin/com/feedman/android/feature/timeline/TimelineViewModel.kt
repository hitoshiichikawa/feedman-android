package com.feedman.android.feature.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feedman.android.core.data.ItemRepository
import com.feedman.android.core.model.ItemSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * UI state holder for the mock-mode timeline (Req 4.4).
 *
 * Maps the [ItemRepository] flow into a [StateFlow] of [TimelineUiState] so that the
 * Composable layer is purely stateless. The skeleton uses the Fake repository
 * binding; switching to the real implementation is a one-line Hilt change.
 */
@HiltViewModel
class TimelineViewModel @Inject constructor(
    repository: ItemRepository,
) : ViewModel() {

    val uiState: StateFlow<TimelineUiState> = repository.observeTimeline()
        .map { TimelineUiState(items = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = TimelineUiState(),
        )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/**
 * Immutable UI state for the timeline screen.
 *
 * @property items Article snapshot to render in the timeline list.
 */
data class TimelineUiState(
    val items: List<ItemSummary> = emptyList(),
)
