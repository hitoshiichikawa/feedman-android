package com.feedman.android.feature.timeline

import androidx.paging.PagingData
import androidx.paging.testing.asSnapshot
import com.feedman.android.core.data.CrossFeedRepository
import com.feedman.android.core.data.ItemDetailRepository
import com.feedman.android.core.data.ItemRepository
import com.feedman.android.core.data.ItemStateFailure
import com.feedman.android.core.data.ItemStateStore
import com.feedman.android.core.model.AppConfig
import com.feedman.android.core.model.CrossFeedItem
import com.feedman.android.core.model.ItemDetail
import com.feedman.android.core.model.MockTimelineItem
import com.feedman.android.core.network.FeedmanException
import com.feedman.android.core.ui.ArticleCardModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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
 * Unit tests for [TimelineViewModel] (Issue #33 / Issue #38).
 *
 * - CrossFeedRepository が返す `Flow<PagingData<CrossFeedItem>>` を ViewModel が
 *   `Flow<PagingData<ArticleCardModel>>` に変換し、`ItemStateStore.overlays` と combine する
 * - mockMode のとき ItemRepository（MockTimelineItem）経由のページに切り替わる
 * - 各カードは記事 ID で安定識別される（Req 5.3 / 5.4）
 * - Issue #38: overlay 反映 / スタートグル / 外部リンク既読化 / overlay 優先合成 を検証
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
    fun `cardPagingData は CrossFeedRepository の items を ArticleCardModel に変換する`() = runTest {
        // Arrange
        val items = listOf(
            crossFeedItem(id = "a", title = "Article A", feedTitle = "Feed A"),
            crossFeedItem(id = "b", title = "Article B", feedTitle = "Feed B"),
        )
        val viewModel = buildViewModel(
            crossFeedRepository = FakeCrossFeedRepository(flowOf(PagingData.from(items))),
        )

        // Act
        val snapshot: List<ArticleCardModel> = viewModel.cardPagingDataForTest().asSnapshot()

        // Assert (Req 1.1〜1.7 / 5.3)
        assertEquals(2, snapshot.size)
        assertEquals("a", snapshot[0].id)
        assertEquals("Article A", snapshot[0].title)
        assertEquals("Feed A", snapshot[0].feedTitle)
        assertEquals("b", snapshot[1].id)
    }

    @Test
    fun `mockMode のとき ItemRepository のスナップショットがカードに変換される`() = runTest {
        // Arrange
        val mockItems = listOf(
            MockTimelineItem(id = "mock-1", title = "Mock 1", feedName = "Feed M", publishedAt = "now"),
        )
        val viewModel = buildViewModel(
            crossFeedRepository = FailingCrossFeedRepository(),
            itemRepository = StubItemRepository(flowOf(mockItems)),
            mockMode = true,
        )

        // Act
        val snapshot = viewModel.cardPagingDataForTest().asSnapshot()

        // Assert
        assertEquals(1, snapshot.size)
        assertEquals("mock-1", snapshot[0].id)
        assertEquals("Mock 1", snapshot[0].title)
        assertEquals("Feed M", snapshot[0].feedTitle)
    }

    @Test
    fun `cardPagingData は空の PagingData も伝播する`() = runTest {
        // Arrange
        val viewModel = buildViewModel(
            crossFeedRepository = FakeCrossFeedRepository(flowOf(PagingData.empty())),
        )

        // Act
        val snapshot = viewModel.cardPagingDataForTest().asSnapshot()

        // Assert (Req 6.2 — 空状態の表示はカード側で 0 件として扱う)
        assertTrue(snapshot.isEmpty())
    }

    @Test
    fun `is_read true な CrossFeedItem は ArticleCardModel に isRead=true で伝播する`() = runTest {
        // Arrange
        val readItem = crossFeedItem(id = "r1", title = "Read", feedTitle = "F").copy(isRead = true)
        val viewModel = buildViewModel(
            crossFeedRepository = FakeCrossFeedRepository(flowOf(PagingData.from(listOf(readItem)))),
        )

        // Act
        val snapshot = viewModel.cardPagingDataForTest().asSnapshot()

        // Assert (Req 2.1)
        assertEquals(true, snapshot.single().isRead)
    }

    // ── Issue #38: overlay 反映 / スタートグル / 外部リンク既読化 ────────

    @Test
    fun `cardPagingData は ItemStateStore overlay 値をサーバー値より優先する_Issue38 Req 3_1`() = runTest {
        // Arrange: サーバーは isStarred=false, isRead=false を返すが overlay は両方 true
        val item = crossFeedItem(id = "ov", title = "Overlay", feedTitle = "F")
        val repo = RecordingItemDetailRepository()
        val store = newStore(repo)
        // overlay を true 上書きする（baseline false）
        store.setStarred(itemId = "ov", isStarred = true, baselineStarred = false)
        store.setRead(itemId = "ov", isRead = true, baselineRead = false)

        val viewModel = buildViewModel(
            crossFeedRepository = FakeCrossFeedRepository(flowOf(PagingData.from(listOf(item)))),
            itemStateStore = store,
        )

        // Act
        val snapshot = viewModel.cardPagingDataForTest().asSnapshot()

        // Assert
        val card = snapshot.single()
        assertEquals("ov", card.id)
        assertEquals(true, card.isStarred) // overlay 優先（Req 3.1）
        assertEquals(true, card.isRead)
    }

    @Test
    fun `overlay にない item はサーバー由来値をそのまま表示する_Issue38 Req 3_3`() = runTest {
        // Arrange: overlay 未設定 / サーバーは isStarred=true
        val item = crossFeedItem(id = "no-ov", title = "No Overlay", feedTitle = "F")
            .copy(isStarred = true)
        val repo = RecordingItemDetailRepository()
        val store = newStore(repo)
        val viewModel = buildViewModel(
            crossFeedRepository = FakeCrossFeedRepository(flowOf(PagingData.from(listOf(item)))),
            itemStateStore = store,
        )

        // Act
        val snapshot = viewModel.cardPagingDataForTest().asSnapshot()

        // Assert
        assertEquals(true, snapshot.single().isStarred)
    }

    @Test
    fun `toggleStar で ItemStateStore_setStarred 経由のサーバー反映が走る_Issue38 Req 1_1_4_4`() = runTest {
        // Arrange
        val repo = RecordingItemDetailRepository()
        val store = newStore(repo)
        val viewModel = buildViewModel(
            crossFeedRepository = FakeCrossFeedRepository(flowOf(PagingData.empty())),
            itemStateStore = store,
        )

        // Act
        viewModel.toggleStar(itemId = "t1", newState = true, baselineStarred = false)

        // Assert
        val starCalls = repo.updateStateCalls.filter { it.isStarred != null }
        assertEquals(1, starCalls.size)
        assertEquals("t1", starCalls[0].itemId)
        assertEquals(true, starCalls[0].isStarred)
    }

    @Test
    fun `markReadOnExternalOpen で既読が立っていなければ ItemStateStore_markRead を呼ぶ_Req 2_2`() = runTest {
        // Arrange
        val repo = RecordingItemDetailRepository()
        val store = newStore(repo)
        val viewModel = buildViewModel(
            crossFeedRepository = FakeCrossFeedRepository(flowOf(PagingData.empty())),
            itemStateStore = store,
        )

        // Act
        viewModel.markReadOnExternalOpen(itemId = "item-1", currentIsRead = false)

        // Assert
        assertEquals(1, repo.updateStateCalls.size)
        assertEquals("item-1", repo.updateStateCalls[0].itemId)
        assertEquals(true, repo.updateStateCalls[0].isRead)
        assertEquals(null, repo.updateStateCalls[0].isStarred)
    }

    @Test
    fun `markReadOnExternalOpen は既読時には API を再送しない_Issue38 Req 5_3`() = runTest {
        // Arrange
        val repo = RecordingItemDetailRepository()
        val store = newStore(repo)
        val viewModel = buildViewModel(
            crossFeedRepository = FakeCrossFeedRepository(flowOf(PagingData.empty())),
            itemStateStore = store,
        )

        // Act
        viewModel.markReadOnExternalOpen(itemId = "item-r", currentIsRead = true)

        // Assert
        assertTrue("既読時は API を呼ばない（冪等）", repo.updateStateCalls.isEmpty())
    }

    @Test
    fun `markReadOnExternalOpen の失敗は ItemStateStore_failures で通知される_Issue38 Req 2_3`() = runTest {
        // Arrange
        val error = FeedmanException(
            code = FeedmanException.CODE_NETWORK_ERROR,
            errorMessage = "オフライン",
        )
        val repo = RecordingItemDetailRepository(errorToThrow = error)
        val store = newStore(repo)
        val received = mutableListOf<ItemStateFailure>()
        val job = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined).launch {
            store.failures.collect { received += it }
        }
        val viewModel = buildViewModel(
            crossFeedRepository = FakeCrossFeedRepository(flowOf(PagingData.empty())),
            itemStateStore = store,
        )

        // Act
        viewModel.markReadOnExternalOpen(itemId = "x", currentIsRead = false)

        // Assert
        assertEquals(1, received.size)
        assertEquals("x", received[0].itemId)
        assertEquals(ItemStateFailure.Kind.Read, received[0].kind)
        // viewModel.itemStateFailures が同じ SharedFlow を再公開している
        assertEquals(store.failures, viewModel.itemStateFailures)
        job.cancel()
    }

    @Test
    fun `notifyExternalLinkFailed で OpenLinkFailed が流れる_Req 3_3_4_1_4_2`() = runTest {
        // Arrange
        val viewModel = buildViewModel(
            crossFeedRepository = FakeCrossFeedRepository(flowOf(PagingData.empty())),
        )
        val received = mutableListOf<TimelineExternalLinkEvent>()
        val job = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined).launch {
            viewModel.externalLinkEvents.collect { received += it }
        }

        // Act
        viewModel.notifyExternalLinkFailed()

        // Assert
        assertEquals(1, received.size)
        assertEquals(TimelineExternalLinkEvent.OpenLinkFailed, received[0])
        job.cancel()
    }

    // ── helpers ──────────────────────────────────────────────────────

    private fun buildViewModel(
        crossFeedRepository: CrossFeedRepository,
        itemRepository: ItemRepository = NoopItemRepository(),
        mockMode: Boolean = false,
        itemStateStore: ItemStateStore = newStore(NoopItemDetailRepository()),
    ): TimelineViewModel = TimelineViewModel(
        crossFeedRepository = crossFeedRepository,
        itemRepository = itemRepository,
        appConfig = AppConfig(baseUrl = "https://example.invalid", mockMode = mockMode),
        itemStateStore = itemStateStore,
    )

    private fun newStore(repo: ItemDetailRepository): ItemStateStore =
        ItemStateStore(repository = repo, scope = CoroutineScope(Dispatchers.Unconfined))

    private fun crossFeedItem(id: String, title: String, feedTitle: String): CrossFeedItem =
        CrossFeedItem(
            id = id,
            feedId = "feed-x",
            feedTitle = feedTitle,
            feedFaviconUrl = null,
            title = title,
            link = "https://example.com/$id",
            summary = "",
            publishedAt = "2026-06-12T11:30:00Z",
            isDateEstimated = false,
            isRead = false,
            isStarred = false,
            hatebuCount = 0,
        )

    private class FakeCrossFeedRepository(
        private val source: Flow<PagingData<CrossFeedItem>>,
    ) : CrossFeedRepository {
        override fun pagingData(): Flow<PagingData<CrossFeedItem>> = source
        override val currentSinceTime: String? = null
    }

    private class FailingCrossFeedRepository : CrossFeedRepository {
        override fun pagingData(): Flow<PagingData<CrossFeedItem>> =
            throw AssertionError("CrossFeedRepository must not be invoked in mockMode")
        override val currentSinceTime: String? = null
    }

    private class StubItemRepository(
        private val source: Flow<List<MockTimelineItem>>,
    ) : ItemRepository {
        override fun observeTimeline(): Flow<List<MockTimelineItem>> = source
    }

    private class NoopItemRepository : ItemRepository {
        override fun observeTimeline(): Flow<List<MockTimelineItem>> = flowOf(emptyList())
    }

    /** Issue #37 / #38: 既読化・スター更新呼び出しを記録する fake。 */
    private class RecordingItemDetailRepository(
        var errorToThrow: FeedmanException? = null,
    ) : ItemDetailRepository {
        val updateStateCalls: MutableList<UpdateCall> = mutableListOf()

        override suspend fun getItem(itemId: String): ItemDetail {
            error("getItem must not be called in this test")
        }

        override suspend fun updateState(
            itemId: String,
            isRead: Boolean?,
            isStarred: Boolean?,
        ) {
            updateStateCalls += UpdateCall(itemId, isRead, isStarred)
            errorToThrow?.let { throw it }
        }
    }

    private class NoopItemDetailRepository : ItemDetailRepository {
        override suspend fun getItem(itemId: String): ItemDetail =
            error("getItem must not be called in this test")

        override suspend fun updateState(
            itemId: String,
            isRead: Boolean?,
            isStarred: Boolean?,
        ) {
            // no-op
        }
    }

    private data class UpdateCall(
        val itemId: String,
        val isRead: Boolean?,
        val isStarred: Boolean?,
    )
}
