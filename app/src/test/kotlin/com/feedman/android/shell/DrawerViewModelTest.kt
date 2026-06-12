package com.feedman.android.shell

import app.cash.turbine.test
import com.feedman.android.core.data.SubscriptionLoadState
import com.feedman.android.core.data.SubscriptionRepository
import com.feedman.android.core.model.Subscription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [DrawerViewModel] の単体テスト（Issue #30 / Req 1.1, 1.3, 1.5, 5.3, NFR 2.1, NFR 3.1）。
 *
 * - Req 1.5: リポジトリが返した順序がそのまま `uiState.rows` に反映される
 * - Req 1.3: リポジトリが空のとき rows も空（フィード行を 1 件も描画しない）
 * - NFR 2.1: リポジトリが新しいリストを emit したら次の uiState に反映される
 * - NFR 3.1: 抽象 [SubscriptionRepository] に依存し、テストで stub に差し替え可能
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DrawerViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `uiState はリポジトリの順序のまま行へ変換する_Req 1_5`() = runTest {
        // Arrange
        val subs = listOf(
            fakeSubscription(feedId = "a", title = "Aフィード", unread = 3, status = "active"),
            fakeSubscription(feedId = "b", title = "Bフィード", unread = 0, status = "stopped"),
            fakeSubscription(feedId = "c", title = "Cフィード", unread = 1, status = "error"),
        )
        val viewModel = DrawerViewModel(repository = StubSubscriptionRepository(flowOf(subs)))

        // Act + Assert
        viewModel.uiState.test {
            var state = awaitItem()
            if (state.rows.isEmpty()) state = awaitItem()
            assertEquals(listOf("a", "b", "c"), state.rows.map { it.feedId })
            assertEquals(
                listOf(FeedStatusIcon.None, FeedStatusIcon.Stopped, FeedStatusIcon.Error),
                state.rows.map { it.statusIcon },
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `リポジトリが空のとき uiState_rows も空_Req 1_3`() = runTest {
        // Arrange
        val viewModel = DrawerViewModel(repository = StubSubscriptionRepository(flowOf(emptyList())))

        // Act + Assert
        viewModel.uiState.test {
            val first = awaitItem()
            assertTrue("初期値または空リスト反映後の rows は空", first.rows.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `リポジトリが新しいリストを emit すると uiState に反映される_NFR 2_1`() = runTest {
        // Arrange
        val source = MutableStateFlow(
            listOf(fakeSubscription(feedId = "a", title = "A", unread = 1, status = "active")),
        )
        val viewModel = DrawerViewModel(
            repository = object : SubscriptionRepository {
                override fun observeSubscriptions(): Flow<List<Subscription>> = source.asStateFlow()
                override fun observeLoadState(): Flow<SubscriptionLoadState> =
                    flowOf(SubscriptionLoadState.Success)
                override suspend fun refresh() = Unit
            },
        )

        // Act + Assert
        viewModel.uiState.test {
            var state = awaitItem()
            if (state.rows.isEmpty()) state = awaitItem()
            assertEquals(listOf("a"), state.rows.map { it.feedId })

            source.value = listOf(
                fakeSubscription(feedId = "a", title = "A", unread = 1, status = "active"),
                fakeSubscription(feedId = "b", title = "B", unread = 5, status = "active"),
            )
            val updated = awaitItem()
            assertEquals(listOf("a", "b"), updated.rows.map { it.feedId })
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun fakeSubscription(
        feedId: String,
        title: String,
        unread: Int,
        status: String,
    ): Subscription = Subscription(
        id = "sub-$feedId",
        userId = "u",
        feedId = feedId,
        feedTitle = title,
        feedUrl = "https://example.com/$feedId",
        faviconUrl = null,
        fetchIntervalMinutes = 30,
        feedStatus = status,
        errorMessage = null,
        unreadCount = unread,
        createdAt = "2025-05-01T00:00:00Z",
    )

    private class StubSubscriptionRepository(
        private val source: Flow<List<Subscription>>,
    ) : SubscriptionRepository {
        override fun observeSubscriptions(): Flow<List<Subscription>> = source
        override fun observeLoadState(): Flow<SubscriptionLoadState> =
            flowOf(SubscriptionLoadState.Success)
        override suspend fun refresh() = Unit
    }
}
