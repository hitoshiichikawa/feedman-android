package com.feedman.android.core.data.fake

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [FakeItemRepository] (Req 1.6, 3.2, 4.4).
 */
class FakeItemRepositoryTest {

    @Test
    fun `observeTimeline emits at least one ItemSummary on subscription`() = runTest {
        // Arrange
        val repository = FakeItemRepository()

        // Act + Assert
        repository.observeTimeline().test {
            val first = awaitItem()
            assertTrue(
                "Fake repository must emit at least one ItemSummary so that the " +
                    "mock-mode timeline is observably non-empty (Req 4.4).",
                first.isNotEmpty(),
            )
            awaitComplete()
        }
    }

    @Test
    fun `observeTimeline preserves item identifiers from the fixed snapshot`() = runTest {
        // Arrange
        val repository = FakeItemRepository()

        // Act + Assert
        repository.observeTimeline().test {
            val items = awaitItem()
            awaitComplete()
            assertEquals(FakeItemRepository.MOCK_ITEMS.map { it.id }, items.map { it.id })
        }
    }

    @Test
    fun `observeTimeline is independent across multiple subscribers`() = runTest {
        // Arrange
        val repository = FakeItemRepository()
        var first: List<com.feedman.android.core.model.ItemSummary> = emptyList()
        var second: List<com.feedman.android.core.model.ItemSummary> = emptyList()

        // Act
        repository.observeTimeline().test {
            first = awaitItem()
            awaitComplete()
        }
        repository.observeTimeline().test {
            second = awaitItem()
            awaitComplete()
        }

        // Assert
        assertEquals(first, second)
    }
}
