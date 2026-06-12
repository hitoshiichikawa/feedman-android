package com.feedman.android.feature.feed

import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingData
import androidx.paging.testing.asSnapshot
import app.cash.turbine.test
import com.feedman.android.core.data.FeedItemFilter
import com.feedman.android.core.data.FeedItemsRepository
import com.feedman.android.core.data.ItemDetailRepository
import com.feedman.android.core.data.ItemStateStore
import com.feedman.android.core.data.SubscriptionLoadState
import com.feedman.android.core.data.SubscriptionRepository
import com.feedman.android.core.model.ItemDetail
import com.feedman.android.core.model.ItemSummary
import com.feedman.android.core.model.Subscription
import com.feedman.android.core.network.FeedmanException
import com.feedman.android.core.ui.ArticleCardModel
import com.feedman.android.shell.AppRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [FeedScreenViewModel] の単体テスト（Issue #41 / requirements.md AC 1〜4 / NFR 1〜3）。
 *
 * 主な検証観点:
 * - feedId の SavedStateHandle 受領（Req 1.1）
 * - filter 切替で FeedItemsRepository.pagingData(feedId, filter) が呼ばれる（Req 2.3）
 * - cardPagingData が FeedItemsRepository の ItemSummary を ArticleCardModel に変換（Req 1.2）
 * - subscription Flow / banner の状態遷移（Req 3.1〜3.4 / 4.1 / 4.3）
 * - onResumeBannerTap の成功 / 失敗 / 進行中フラグ（Req 3.5 / 3.6 / 3.7 / 3.8）
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedScreenViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── feedId / 初期化 ───────────────────────────────────────────

    @Test
    fun `feedId が SavedStateHandle から取得される_Req 1_1`() = runTest {
        // Arrange
        val vm = buildViewModel(feedId = "feed-xyz")

        // Assert
        assertEquals("feed-xyz", vm.feedId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `feedId が空白のとき例外で早期検出する`() {
        buildViewModel(feedId = "  ")
    }

    @Test
    fun `init で SubscriptionRepository_refresh が呼ばれる_Req 4_2`() = runTest {
        // Arrange
        val repo = RecordingSubscriptionRepository()

        // Act
        buildViewModel(subscriptionRepository = repo)

        // Assert
        assertEquals(1, repo.refreshCount)
    }

    // ── フィルタタブ ─────────────────────────────────────────────

    @Test
    fun `初期 currentFilter は ALL_Req 2_2`() = runTest {
        // Arrange
        val vm = buildViewModel()

        // Assert
        assertEquals(FeedFilter.ALL, vm.currentFilter.value)
    }

    @Test
    fun `selectFilter で currentFilter が更新される_Req 2_3`() = runTest {
        // Arrange
        val vm = buildViewModel()

        // Act
        vm.selectFilter(FeedFilter.UNREAD)

        // Assert
        assertEquals(FeedFilter.UNREAD, vm.currentFilter.value)
    }

    // ── 一覧ページング ──────────────────────────────────────────

    @Test
    fun `cardPagingData は ItemSummary を ArticleCardModel に変換する_Req 1_2`() = runTest {
        // Arrange
        val items = listOf(
            itemSummary(id = "x1", title = "Article X1"),
            itemSummary(id = "x2", title = "Article X2"),
        )
        val feedRepo = RecordingFeedItemsRepository(flowOf(PagingData.from(items)))
        val vm = buildViewModel(feedItemsRepository = feedRepo, feedId = "f1")

        // Act
        val snapshot: List<ArticleCardModel> = vm.cardPagingDataForTest(FeedFilter.ALL).asSnapshot()

        // Assert
        assertEquals(2, snapshot.size)
        assertEquals("x1", snapshot[0].id)
        assertEquals("Article X1", snapshot[0].title)
        assertEquals("x2", snapshot[1].id)
        // pagingData が呼ばれた引数を確認
        assertTrue(
            feedRepo.calls.isNotEmpty(),
        )
        assertEquals("f1", feedRepo.calls[0].feedId)
        assertEquals(FeedItemFilter.ALL, feedRepo.calls[0].filter)
    }

    @Test
    fun `フィルタを変えると pagingData が新しい filter で再呼び出しされる_Req 2_3 2_4`() = runTest {
        // Arrange
        val feedRepo = RecordingFeedItemsRepository(flowOf(PagingData.empty()))
        val vm = buildViewModel(feedItemsRepository = feedRepo, feedId = "f1")
        // 一度初期 filter で snapshot を作って Pager を起動する
        vm.cardPagingDataForTest(FeedFilter.ALL).asSnapshot()
        val callsBefore = feedRepo.calls.size

        // Act: フィルタを UNREAD に切り替えて再 snapshot
        vm.selectFilter(FeedFilter.UNREAD)
        vm.cardPagingDataForTest(FeedFilter.UNREAD).asSnapshot()

        // Assert: 新しい filter (UNREAD) で呼ばれている
        val unreadCalls = feedRepo.calls.drop(callsBefore).filter { it.filter == FeedItemFilter.UNREAD }
        assertTrue(unreadCalls.isNotEmpty())
        assertEquals("f1", unreadCalls[0].feedId)
    }

    @Test
    fun `空 PagingData も伝播する_Req 1_4`() = runTest {
        // Arrange
        val vm = buildViewModel(
            feedItemsRepository = RecordingFeedItemsRepository(flowOf(PagingData.empty())),
        )

        // Act
        val snapshot = vm.cardPagingDataForTest(FeedFilter.ALL).asSnapshot()

        // Assert
        assertTrue(snapshot.isEmpty())
    }

    // ── 購読情報 / バナー ───────────────────────────────────────

    @Test
    fun `subscription Flow は SubscriptionRepository_observeFeed のスナップショットを公開する_Req 4_1`() = runTest {
        // Arrange
        val source = MutableStateFlow(
            listOf(subscription(feedId = "f1", status = "active")),
        )
        val repo = StubSubscriptionRepository(source)
        val vm = buildViewModel(subscriptionRepository = repo, feedId = "f1")

        // Act + Assert
        vm.subscription.test {
            var first = awaitItem()
            if (first == null) first = awaitItem()
            assertNotNull(first)
            assertEquals("f1", first!!.feedId)
            assertEquals("active", first.feedStatus)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `subscription が未存在のとき null を流す_Req 4_3`() = runTest {
        // Arrange: feedId に対応する subscription を含まないリスト
        val source = MutableStateFlow(
            listOf(subscription(feedId = "other-feed", status = "active")),
        )
        val repo = StubSubscriptionRepository(source)
        val vm = buildViewModel(subscriptionRepository = repo, feedId = "missing-feed")

        // Act + Assert
        vm.subscription.test {
            val first = awaitItem()
            assertNull(first)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `banner は subscription stopped のとき Visible STOPPED になる_Req 3_1 3_2`() = runTest {
        // Arrange
        val source = MutableStateFlow(
            listOf(
                subscription(
                    feedId = "f1",
                    status = "stopped",
                    errorMessage = "手動停止",
                ),
            ),
        )
        val vm = buildViewModel(
            subscriptionRepository = StubSubscriptionRepository(source),
            feedId = "f1",
        )

        // Act + Assert
        vm.banner.test {
            var state = awaitItem()
            // 初期 Hidden → Visible 遷移を許容する
            while (state !is FeedStatusBanner.Visible) state = awaitItem()
            assertEquals(FeedStatusBanner.Kind.STOPPED, state.kind)
            assertEquals("手動停止", state.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `banner は active のとき Hidden_Req 3_4`() = runTest {
        // Arrange
        val source = MutableStateFlow(
            listOf(subscription(feedId = "f1", status = "active")),
        )
        val vm = buildViewModel(
            subscriptionRepository = StubSubscriptionRepository(source),
            feedId = "f1",
        )

        // Assert: 取得後も Hidden のまま
        vm.banner.test {
            // Hidden のみが流れる
            val state = awaitItem()
            assertEquals(FeedStatusBanner.Hidden, state)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── 再開アクション ────────────────────────────────────────

    @Test
    fun `onResumeBannerTap で SubscriptionRepository_resume が呼ばれ ResumeSucceeded を流す_Req 3_5 3_7`() =
        runTest {
            // Arrange
            val source = MutableStateFlow(
                listOf(
                    subscription(feedId = "f1", id = "sub-f1", status = "error", errorMessage = "失敗"),
                ),
            )
            val repo = StubSubscriptionRepository(source)
            val vm = buildViewModel(subscriptionRepository = repo, feedId = "f1")
            // subscription が StateFlow に乗るまで待つ
            vm.subscription.test {
                while (awaitItem() == null) Unit
                cancelAndIgnoreRemainingEvents()
            }
            val received = mutableListOf<FeedScreenEvent>()
            val job = CoroutineScope(Dispatchers.Unconfined).launch {
                vm.events.collect { received += it }
            }

            // Act
            vm.onResumeBannerTap()

            // Assert
            assertEquals(1, repo.resumeCalls.size)
            assertEquals("sub-f1", repo.resumeCalls[0])
            assertTrue(received.isNotEmpty())
            assertEquals(FeedScreenEvent.ResumeSucceeded, received[0])
            job.cancel()
        }

    @Test
    fun `onResumeBannerTap 失敗時は ResumeFailed をエラーメッセージ付きで流す_Req 3_8`() = runTest {
        // Arrange
        val source = MutableStateFlow(
            listOf(subscription(feedId = "f1", id = "sub-f1", status = "error")),
        )
        val repo = StubSubscriptionRepository(
            source = source,
            resumeError = FeedmanException(
                code = "UPSTREAM_ERROR",
                errorMessage = "再開に失敗しました",
            ),
        )
        val vm = buildViewModel(subscriptionRepository = repo, feedId = "f1")
        // subscription が StateFlow に乗るまで待つ
        vm.subscription.test {
            while (awaitItem() == null) Unit
            cancelAndIgnoreRemainingEvents()
        }
        val received = mutableListOf<FeedScreenEvent>()
        val job = CoroutineScope(Dispatchers.Unconfined).launch {
            vm.events.collect { received += it }
        }

        // Act
        vm.onResumeBannerTap()

        // Assert
        assertTrue(received.isNotEmpty())
        val failed = received.filterIsInstance<FeedScreenEvent.ResumeFailed>().first()
        assertEquals("再開に失敗しました", failed.message)
        job.cancel()
    }

    @Test
    fun `onResumeBannerTap 進行中フラグが banner に伝搬する_Req 3_6`() = runTest {
        // Arrange: resume をブロックする repo
        val source = MutableStateFlow(
            listOf(subscription(feedId = "f1", id = "sub-f1", status = "error")),
        )
        val repo = BlockingResumeRepository(source)
        val vm = buildViewModel(subscriptionRepository = repo, feedId = "f1")

        // Act + Assert: banner を購読しつつ resume を起動し、resumeInProgress=true 状態を観測する
        vm.banner.test {
            // 初期 Hidden → subscription を観測した直後の Visible(progress=false) の順で流れる
            var state = awaitItem()
            while (state !is FeedStatusBanner.Visible) state = awaitItem()
            assertEquals(false, state.resumeInProgress)

            // 再開を起動して進行中状態の遷移を待つ
            vm.onResumeBannerTap()
            var progress = awaitItem()
            while (progress is FeedStatusBanner.Visible && !progress.resumeInProgress) {
                progress = awaitItem()
            }
            assertTrue(progress is FeedStatusBanner.Visible)
            assertEquals(true, (progress as FeedStatusBanner.Visible).resumeInProgress)

            // 後始末
            repo.complete(source.value[0].copy(feedStatus = "active", errorMessage = null))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `subscription が null のとき onResumeBannerTap は no-op`() = runTest {
        // Arrange
        val source = MutableStateFlow<List<Subscription>>(emptyList())
        val repo = StubSubscriptionRepository(source)
        val vm = buildViewModel(subscriptionRepository = repo, feedId = "missing")
        // subscription が null のまま
        assertNull(vm.subscription.value)

        // Act
        vm.onResumeBannerTap()

        // Assert
        assertEquals(0, repo.resumeCalls.size)
    }

    // ── Pull-to-refresh（Issue #42） ───────────────────────────

    @Test
    fun `onPullToRefresh で SubscriptionRepository_fetch が呼ばれ FetchSucceeded を流す_Issue42 Req 1_1 2_1`() =
        runTest {
            // Arrange
            val source = MutableStateFlow(
                listOf(subscription(feedId = "f1", id = "sub-f1", status = "active")),
            )
            val repo = StubSubscriptionRepository(source)
            val vm = buildViewModel(subscriptionRepository = repo, feedId = "f1")
            vm.subscription.test {
                while (awaitItem() == null) Unit
                cancelAndIgnoreRemainingEvents()
            }
            val received = mutableListOf<FeedScreenEvent>()
            val job = CoroutineScope(Dispatchers.Unconfined).launch {
                vm.events.collect { received += it }
            }

            // Act
            vm.onPullToRefresh()

            // Assert
            assertEquals(1, repo.fetchCalls.size)
            assertEquals("sub-f1", repo.fetchCalls[0])
            assertTrue(received.isNotEmpty())
            assertEquals(FeedScreenEvent.FetchSucceeded, received[0])
            job.cancel()
        }

    @Test
    fun `onPullToRefresh が FEED_COOLDOWN のとき FetchCooldown を retryAfterSeconds 付きで流す_Issue42 Req 3_1 3_2`() =
        runTest {
            // Arrange
            val source = MutableStateFlow(
                listOf(subscription(feedId = "f1", id = "sub-f1", status = "active")),
            )
            val repo = StubSubscriptionRepository(
                source = source,
                fetchError = FeedmanException(
                    code = FeedmanException.CODE_FEED_COOLDOWN,
                    errorMessage = "クールダウン中です。",
                    retryAfterSeconds = 30,
                    httpStatus = 429,
                ),
            )
            val vm = buildViewModel(subscriptionRepository = repo, feedId = "f1")
            vm.subscription.test {
                while (awaitItem() == null) Unit
                cancelAndIgnoreRemainingEvents()
            }
            val received = mutableListOf<FeedScreenEvent>()
            val job = CoroutineScope(Dispatchers.Unconfined).launch {
                vm.events.collect { received += it }
            }

            // Act
            vm.onPullToRefresh()

            // Assert
            val cooldown = received.filterIsInstance<FeedScreenEvent.FetchCooldown>().first()
            assertEquals(30, cooldown.retryAfterSeconds)
            job.cancel()
        }

    @Test
    fun `onPullToRefresh が FEED_COOLDOWN かつ retryAfterSeconds 欠落のとき null を流す_Issue42 Req 3_3`() =
        runTest {
            // Arrange
            val source = MutableStateFlow(
                listOf(subscription(feedId = "f1", id = "sub-f1", status = "active")),
            )
            val repo = StubSubscriptionRepository(
                source = source,
                fetchError = FeedmanException(
                    code = FeedmanException.CODE_FEED_COOLDOWN,
                    errorMessage = "クールダウン中です。",
                    retryAfterSeconds = null,
                    httpStatus = 429,
                ),
            )
            val vm = buildViewModel(subscriptionRepository = repo, feedId = "f1")
            vm.subscription.test {
                while (awaitItem() == null) Unit
                cancelAndIgnoreRemainingEvents()
            }
            val received = mutableListOf<FeedScreenEvent>()
            val job = CoroutineScope(Dispatchers.Unconfined).launch {
                vm.events.collect { received += it }
            }

            // Act
            vm.onPullToRefresh()

            // Assert
            val cooldown = received.filterIsInstance<FeedScreenEvent.FetchCooldown>().first()
            assertNull(cooldown.retryAfterSeconds)
            job.cancel()
        }

    @Test
    fun `onPullToRefresh がその他エラーのとき FetchFailed を message 付きで流す_Issue42 Req 4_1`() = runTest {
        // Arrange
        val source = MutableStateFlow(
            listOf(subscription(feedId = "f1", id = "sub-f1", status = "active")),
        )
        val repo = StubSubscriptionRepository(
            source = source,
            fetchError = FeedmanException(
                code = "UPSTREAM_ERROR",
                errorMessage = "上流サービスでエラー",
                httpStatus = 503,
            ),
        )
        val vm = buildViewModel(subscriptionRepository = repo, feedId = "f1")
        vm.subscription.test {
            while (awaitItem() == null) Unit
            cancelAndIgnoreRemainingEvents()
        }
        val received = mutableListOf<FeedScreenEvent>()
        val job = CoroutineScope(Dispatchers.Unconfined).launch {
            vm.events.collect { received += it }
        }

        // Act
        vm.onPullToRefresh()

        // Assert
        val failed = received.filterIsInstance<FeedScreenEvent.FetchFailed>().first()
        assertEquals("上流サービスでエラー", failed.message)
        job.cancel()
    }

    @Test
    fun `onPullToRefresh がネットワークエラーのとき FetchFailed をネットワーク文言で流す_Issue42 Req 4_3`() =
        runTest {
            // Arrange
            val source = MutableStateFlow(
                listOf(subscription(feedId = "f1", id = "sub-f1", status = "active")),
            )
            val repo = StubSubscriptionRepository(
                source = source,
                fetchError = FeedmanException(
                    code = FeedmanException.CODE_NETWORK_ERROR,
                    errorMessage = FeedmanException.FALLBACK_NETWORK_MESSAGE,
                ),
            )
            val vm = buildViewModel(subscriptionRepository = repo, feedId = "f1")
            vm.subscription.test {
                while (awaitItem() == null) Unit
                cancelAndIgnoreRemainingEvents()
            }
            val received = mutableListOf<FeedScreenEvent>()
            val job = CoroutineScope(Dispatchers.Unconfined).launch {
                vm.events.collect { received += it }
            }

            // Act
            vm.onPullToRefresh()

            // Assert
            val failed = received.filterIsInstance<FeedScreenEvent.FetchFailed>().first()
            assertEquals(FeedmanException.FALLBACK_NETWORK_MESSAGE, failed.message)
            job.cancel()
        }

    @Test
    fun `onPullToRefresh は進行中の追加起動を抑止する_Issue42 Req 1_4`() = runTest {
        // Arrange: fetch をブロックする repo
        val source = MutableStateFlow(
            listOf(subscription(feedId = "f1", id = "sub-f1", status = "active")),
        )
        val repo = BlockingFetchRepository(source)
        val vm = buildViewModel(subscriptionRepository = repo, feedId = "f1")
        vm.subscription.test {
            while (awaitItem() == null) Unit
            cancelAndIgnoreRemainingEvents()
        }

        // Act: 1 回目は進行中、2 回目以降は抑止される
        vm.onPullToRefresh()
        assertTrue(vm.fetchInProgress.value)
        vm.onPullToRefresh()
        vm.onPullToRefresh()

        // Assert: fetch は 1 回だけ呼ばれる
        assertEquals(1, repo.fetchInvocations)

        // 後始末
        repo.complete(source.value[0])
    }

    @Test
    fun `onPullToRefresh は subscription null のとき no-op`() = runTest {
        // Arrange
        val source = MutableStateFlow<List<Subscription>>(emptyList())
        val repo = StubSubscriptionRepository(source)
        val vm = buildViewModel(subscriptionRepository = repo, feedId = "missing")
        assertNull(vm.subscription.value)

        // Act
        vm.onPullToRefresh()

        // Assert
        assertEquals(0, repo.fetchCalls.size)
        assertEquals(false, vm.fetchInProgress.value)
    }

    @Test
    fun `onPullToRefresh 完了後 fetchInProgress が false に戻る_Issue42 NFR 1_2`() = runTest {
        // Arrange
        val source = MutableStateFlow(
            listOf(subscription(feedId = "f1", id = "sub-f1", status = "active")),
        )
        val repo = StubSubscriptionRepository(source)
        val vm = buildViewModel(subscriptionRepository = repo, feedId = "f1")
        vm.subscription.test {
            while (awaitItem() == null) Unit
            cancelAndIgnoreRemainingEvents()
        }

        // Act
        vm.onPullToRefresh()

        // Assert
        assertEquals(false, vm.fetchInProgress.value)
    }

    // ── 外部リンク失敗通知 ─────────────────────────────────────

    @Test
    fun `notifyExternalLinkFailed で OpenLinkFailed が流れる`() = runTest {
        // Arrange
        val vm = buildViewModel()
        val received = mutableListOf<FeedScreenEvent>()
        val job = CoroutineScope(Dispatchers.Unconfined).launch {
            vm.events.collect { received += it }
        }

        // Act
        vm.notifyExternalLinkFailed()

        // Assert
        assertEquals(1, received.size)
        assertEquals(FeedScreenEvent.OpenLinkFailed, received[0])
        job.cancel()
    }

    // ── helpers ──────────────────────────────────────────────

    private fun buildViewModel(
        feedId: String = "feed-default",
        feedItemsRepository: FeedItemsRepository = RecordingFeedItemsRepository(flowOf(PagingData.empty())),
        subscriptionRepository: SubscriptionRepository = StubSubscriptionRepository(
            MutableStateFlow(emptyList()),
        ),
        itemStateStore: ItemStateStore = newStore(),
    ): FeedScreenViewModel {
        val savedState = SavedStateHandle(
            mapOf(AppRoute.Feed.ARG_FEED_ID to feedId),
        )
        return FeedScreenViewModel(
            savedStateHandle = savedState,
            feedItemsRepository = feedItemsRepository,
            subscriptionRepository = subscriptionRepository,
            itemStateStore = itemStateStore,
        )
    }

    private fun newStore(): ItemStateStore = ItemStateStore(
        repository = object : ItemDetailRepository {
            override suspend fun getItem(itemId: String): ItemDetail =
                error("getItem must not be called")
            override suspend fun updateState(itemId: String, isRead: Boolean?, isStarred: Boolean?) =
                Unit
        },
        scope = CoroutineScope(Dispatchers.Unconfined),
    )

    private fun itemSummary(id: String, title: String): ItemSummary = ItemSummary(
        id = id,
        feedId = "feed-x",
        title = title,
        link = "https://example.com/$id",
        summary = "",
        publishedAt = "2026-06-12T11:30:00Z",
        isDateEstimated = false,
        isRead = false,
        isStarred = false,
        hatebuCount = 0,
        hatebuFetchedAt = null,
    )

    private fun subscription(
        feedId: String,
        id: String = "sub-$feedId",
        status: String,
        errorMessage: String? = null,
    ): Subscription = Subscription(
        id = id,
        userId = "u",
        feedId = feedId,
        feedTitle = "Sample $feedId",
        feedUrl = "https://example.com/$feedId",
        faviconUrl = null,
        fetchIntervalMinutes = 60,
        feedStatus = status,
        errorMessage = errorMessage,
        unreadCount = 0,
        createdAt = "2026-01-01T00:00:00Z",
    )

    // ── テスト用 fake ────────────────────────────────────────

    private class RecordingFeedItemsRepository(
        private val source: Flow<PagingData<ItemSummary>>,
    ) : FeedItemsRepository {
        data class Call(val feedId: String, val filter: FeedItemFilter)
        val calls: MutableList<Call> = mutableListOf()

        override fun pagingData(
            feedId: String,
            filter: FeedItemFilter,
        ): Flow<PagingData<ItemSummary>> {
            calls += Call(feedId, filter)
            return source
        }
    }

    private class StubSubscriptionRepository(
        private val source: MutableStateFlow<List<Subscription>>,
        private val resumeError: Throwable? = null,
        private val fetchError: Throwable? = null,
    ) : SubscriptionRepository {
        val resumeCalls: MutableList<String> = mutableListOf()
        val fetchCalls: MutableList<String> = mutableListOf()

        override fun observeSubscriptions(): Flow<List<Subscription>> = source.asStateFlow()

        override fun observeLoadState(): Flow<SubscriptionLoadState> =
            flowOf(SubscriptionLoadState.Success)

        override suspend fun refresh() = Unit

        override suspend fun resume(subscriptionId: String): Subscription {
            resumeCalls += subscriptionId
            resumeError?.let { throw it }
            val updated = source.value
                .firstOrNull { it.id == subscriptionId }
                ?.copy(feedStatus = "active", errorMessage = null)
                ?: error("resume: not found $subscriptionId")
            // 状態を内部 source にも反映する（実装と同じ流儀）
            source.value = source.value.map {
                if (it.id == subscriptionId) updated else it
            }
            return updated
        }

        override suspend fun fetch(subscriptionId: String): Subscription {
            fetchCalls += subscriptionId
            fetchError?.let { throw it }
            val updated = source.value
                .firstOrNull { it.id == subscriptionId }
                ?: error("fetch: not found $subscriptionId")
            source.value = source.value.map {
                if (it.id == subscriptionId) updated else it
            }
            return updated
        }
    }

    private class RecordingSubscriptionRepository : SubscriptionRepository {
        var refreshCount: Int = 0
            private set
        override fun observeSubscriptions(): Flow<List<Subscription>> = flowOf(emptyList())
        override fun observeLoadState(): Flow<SubscriptionLoadState> =
            flowOf(SubscriptionLoadState.Success)
        override suspend fun refresh() {
            refreshCount++
        }
    }

    /** Req 3.6: resume() を呼び出し側でブロックしたまま、進行中状態を観測するため。 */
    private class BlockingResumeRepository(
        private val source: MutableStateFlow<List<Subscription>>,
    ) : SubscriptionRepository {
        private val signal = kotlinx.coroutines.CompletableDeferred<Subscription>()

        override fun observeSubscriptions(): Flow<List<Subscription>> = source.asStateFlow()
        override fun observeLoadState(): Flow<SubscriptionLoadState> =
            flowOf(SubscriptionLoadState.Success)
        override suspend fun refresh() = Unit

        override suspend fun resume(subscriptionId: String): Subscription = signal.await()

        fun complete(returnValue: Subscription) {
            signal.complete(returnValue)
        }
    }

    /** Issue #42 Req 1.4: fetch() を呼び出し側でブロックしたまま、進行中状態と重複抑止を観測する。 */
    private class BlockingFetchRepository(
        private val source: MutableStateFlow<List<Subscription>>,
    ) : SubscriptionRepository {
        private val signal = kotlinx.coroutines.CompletableDeferred<Subscription>()
        var fetchInvocations: Int = 0
            private set

        override fun observeSubscriptions(): Flow<List<Subscription>> = source.asStateFlow()
        override fun observeLoadState(): Flow<SubscriptionLoadState> =
            flowOf(SubscriptionLoadState.Success)
        override suspend fun refresh() = Unit

        override suspend fun fetch(subscriptionId: String): Subscription {
            fetchInvocations++
            return signal.await()
        }

        fun complete(returnValue: Subscription) {
            signal.complete(returnValue)
        }
    }
}
