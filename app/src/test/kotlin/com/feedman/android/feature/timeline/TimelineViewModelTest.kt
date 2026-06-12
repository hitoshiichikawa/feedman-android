package com.feedman.android.feature.timeline

import app.cash.turbine.test
import com.feedman.android.core.data.ItemRepository
import com.feedman.android.core.model.ItemSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [TimelineViewModel] (Req 4.4).
 *
 * Exercises Hilt-injected dependency by constructing the ViewModel directly with a
 * stub [ItemRepository] (the Hilt graph itself is covered by integration tests in
 * subsequent Issues).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimelineViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `uiState exposes items emitted by the injected repository`() = runTest {
        // Arrange
        val items = listOf(
            ItemSummary(id = "a", title = "t-a", feedName = "f", publishedAt = "now"),
            ItemSummary(id = "b", title = "t-b", feedName = "f", publishedAt = "now"),
        )
        val viewModel = TimelineViewModel(repository = StubItemRepository(flowOf(items)))

        // Act + Assert
        viewModel.uiState.test {
            // Initial state may be the empty default before stateIn collects the flow;
            // skip until we see the populated snapshot.
            var state = awaitItem()
            if (state.items.isEmpty()) {
                state = awaitItem()
            }
            assertEquals(items, state.items)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `uiState defaults to empty when repository emits nothing`() = runTest {
        // Arrange
        val viewModel = TimelineViewModel(repository = StubItemRepository(flowOf(emptyList())))

        // Act + Assert
        viewModel.uiState.test {
            val first = awaitItem()
            assertEquals(emptyList<ItemSummary>(), first.items)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private class StubItemRepository(
        private val source: Flow<List<ItemSummary>>,
    ) : ItemRepository {
        override fun observeTimeline(): Flow<List<ItemSummary>> = source
    }
}
