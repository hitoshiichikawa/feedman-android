package com.feedman.android.feature.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feedman.android.R
import com.feedman.android.core.model.MockTimelineItem

/**
 * Stateful entry point for the timeline route (Req 4.4).
 *
 * Collects [TimelineUiState] from the Hilt-provided ViewModel and delegates rendering
 * to the stateless [TimelineList] Composable so that previews and tests can drive UI
 * with synthetic state.
 */
@Composable
fun TimelineScreen(
    modifier: Modifier = Modifier,
    viewModel: TimelineViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TimelineList(state = state, modifier = modifier)
}

/**
 * Stateless timeline list (Req 4.4).
 */
@Composable
internal fun TimelineList(
    state: TimelineUiState,
    modifier: Modifier = Modifier,
) {
    if (state.items.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.timeline_empty))
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items = state.items, key = MockTimelineItem::id) { item ->
            TimelineRow(item = item)
        }
    }
}

@Composable
private fun TimelineRow(item: MockTimelineItem) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "${item.feedName} ・ ${item.publishedAt}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
