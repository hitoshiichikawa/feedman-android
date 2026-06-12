package com.feedman.android.feature.timeline

import androidx.paging.PagingData
import androidx.paging.testing.asSnapshot
import com.feedman.android.core.data.CrossFeedRepository
import com.feedman.android.core.data.ItemRepository
import com.feedman.android.core.model.AppConfig
import com.feedman.android.core.model.CrossFeedItem
import com.feedman.android.core.model.MockTimelineItem
import com.feedman.android.core.ui.ArticleCardModel
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [TimelineViewModel] (Issue #33 Req 5.1〜5.3 / 2.1 / 2.2 / 1.1〜1.7).
 *
 * - CrossFeedRepository が返す `Flow<PagingData<CrossFeedItem>>` を ViewModel が
 *   `Flow<PagingData<ArticleCardModel>>` に変換して公開する
 * - mockMode のとき ItemRepository（MockTimelineItem）経由のページに切り替わる
 * - 各カードは記事 ID で安定識別される（Req 5.3 / 5.4）
 *
 * Paging 3 の検証には `androidx.paging.testing.asSnapshot` を使い、エミュレータ無しの
 * JVM テストで PagingData を List に展開する。
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
        val viewModel = TimelineViewModel(
            crossFeedRepository = FakeCrossFeedRepository(flowOf(PagingData.from(items))),
            itemRepository = NoopItemRepository(),
            appConfig = AppConfig(baseUrl = "https://example.invalid", mockMode = false),
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
        val viewModel = TimelineViewModel(
            crossFeedRepository = FailingCrossFeedRepository(), // mockMode では呼ばれない
            itemRepository = StubItemRepository(flowOf(mockItems)),
            appConfig = AppConfig(baseUrl = "https://example.invalid", mockMode = true),
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
        val viewModel = TimelineViewModel(
            crossFeedRepository = FakeCrossFeedRepository(flowOf(PagingData.empty())),
            itemRepository = NoopItemRepository(),
            appConfig = AppConfig(baseUrl = "https://example.invalid", mockMode = false),
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
        val viewModel = TimelineViewModel(
            crossFeedRepository = FakeCrossFeedRepository(flowOf(PagingData.from(listOf(readItem)))),
            itemRepository = NoopItemRepository(),
            appConfig = AppConfig(baseUrl = "https://example.invalid", mockMode = false),
        )

        // Act
        val snapshot = viewModel.cardPagingDataForTest().asSnapshot()

        // Assert (Req 2.1)
        assertEquals(true, snapshot.single().isRead)
    }

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
}
