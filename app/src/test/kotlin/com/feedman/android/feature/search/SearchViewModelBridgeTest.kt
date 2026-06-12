package com.feedman.android.feature.search

import androidx.paging.PagingData
import androidx.paging.testing.asSnapshot
import com.feedman.android.core.data.ItemDetailRepository
import com.feedman.android.core.data.ItemStateFailure
import com.feedman.android.core.data.ItemStateStore
import com.feedman.android.core.data.SearchRepository
import com.feedman.android.core.model.ItemDetail
import com.feedman.android.core.model.ItemSearchHit
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [SearchViewModel] の Issue #48（検索→詳細ブリッジ）追加 API 検証。
 *
 * - `cardPagingData` の overlay 合成（Req 3.1 / 3.2 / 3.3 / 3.4 / 3.5）
 * - `toggleStar` / `markReadOnExternalOpen` / `notifyExternalLinkFailed` の委譲（Req 2.3 / 2.4 / 3.4）
 * - `itemStateFailures` 再公開（Req 2.4 のロールバック通知）
 *
 * 既存 [SearchViewModelTest] は Issue #47 で確定済みの ViewModel API 表面（queryInput / submit /
 * resultsPaging）を引き続き検証する。本テストは #47 テストと独立した観点に絞る。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelBridgeTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---- Req 3.5: overlay が無いとき、サーバー由来 ItemSearchHit の値がそのまま使われる -----

    @Test
    fun `Req 3-5 overlay が無いとき検索ヒットの isRead isStarred はそのまま反映される`() = runTest {
        // Arrange: overlay 空のまま、サーバー由来値だけが映る
        val hits = listOf(
            searchHit(id = "x1", title = "X1", isRead = true, isStarred = false),
            searchHit(id = "x2", title = "X2", isRead = false, isStarred = true),
        )
        val viewModel = buildViewModel(
            searchRepository = StaticSearchRepository(flowOf(PagingData.from(hits))),
        )

        // Act: 検索を確定して結果を取得
        viewModel.onQueryChanged("kotlin")
        viewModel.submit()
        val snapshot: List<ArticleCardModel> = viewModel.cardPagingDataForTest().asSnapshot()

        // Assert
        assertEquals(2, snapshot.size)
        assertEquals(
            "Req 3.5: overlay 無しなら ItemSearchHit の isRead がそのまま反映される",
            true,
            snapshot[0].isRead,
        )
        assertEquals(false, snapshot[0].isStarred)
        assertEquals(false, snapshot[1].isRead)
        assertEquals(true, snapshot[1].isStarred)
    }

    // ---- Req 3.1 / 3.2 / 3.3: overlay の既読値はサーバー値より優先される -------------

    @Test
    fun `Req 3-3 overlay isRead=true の上書き値が検索ヒットの isRead=false を上書きする`() = runTest {
        // Arrange: ItemSearchHit は isRead=false を返すが overlay は true（外部リンク経由の既読化等）
        val hit = searchHit(id = "ov-read", title = "OvRead", isRead = false, isStarred = false)
        val store = newStore(NoopItemDetailRepository())
        // 直前に外部リンク or 詳細シート起動由来で既読化されたと仮定
        store.markRead(itemId = "ov-read", currentIsRead = false)

        val viewModel = buildViewModel(
            searchRepository = StaticSearchRepository(flowOf(PagingData.from(listOf(hit)))),
            itemStateStore = store,
        )

        // Act
        viewModel.onQueryChanged("kotlin")
        viewModel.submit()
        val snapshot = viewModel.cardPagingDataForTest().asSnapshot()

        // Assert
        val card = snapshot.single()
        assertEquals(
            "Req 3.3: overlay の isRead=true がサーバー由来 false より優先される",
            true,
            card.isRead,
        )
    }

    // ---- Req 3.1 / 3.4: overlay の isStarred 値が検索ヒットの isStarred を上書きする -----

    @Test
    fun `Req 3-4 overlay isStarred=true の上書き値が検索ヒットの isStarred=false を上書きする`() = runTest {
        // Arrange: 詳細シート側でスタートグルされたと仮定
        val hit = searchHit(id = "ov-star", title = "OvStar", isRead = false, isStarred = false)
        val store = newStore(NoopItemDetailRepository())
        store.setStarred(itemId = "ov-star", isStarred = true, baselineStarred = false)

        val viewModel = buildViewModel(
            searchRepository = StaticSearchRepository(flowOf(PagingData.from(listOf(hit)))),
            itemStateStore = store,
        )

        // Act
        viewModel.onQueryChanged("kotlin")
        viewModel.submit()
        val snapshot = viewModel.cardPagingDataForTest().asSnapshot()

        // Assert
        val card = snapshot.single()
        assertEquals(
            "Req 3.4: overlay の isStarred=true がサーバー由来 false より優先される",
            true,
            card.isStarred,
        )
    }

    // ---- Req 3.6: 追加ページ読込後も overlay 上書きが維持される ------------------------

    @Test
    fun `Req 3-6 追加ページ読込後も overlay 上書き値は新規ヒット側で維持される`() = runTest {
        // Arrange: 「追加ページ」相当として、サーバーが 2 件返してくる状況下で overlay は
        // 既に 1 件目を既読化済み。新規ページ合成後も overlay 値が優先される。
        val hits = listOf(
            searchHit(id = "p1", title = "P1", isRead = false, isStarred = false),
            searchHit(id = "p2", title = "P2", isRead = false, isStarred = false),
        )
        val store = newStore(NoopItemDetailRepository())
        store.markRead(itemId = "p1", currentIsRead = false)

        val viewModel = buildViewModel(
            searchRepository = StaticSearchRepository(flowOf(PagingData.from(hits))),
            itemStateStore = store,
        )

        // Act: 検索確定後に paging 全件のスナップショットを取る
        viewModel.onQueryChanged("kotlin")
        viewModel.submit()
        val snapshot = viewModel.cardPagingDataForTest().asSnapshot()

        // Assert
        assertEquals(2, snapshot.size)
        assertEquals("Req 3.6: overlay 済みは isRead=true を維持", true, snapshot[0].isRead)
        assertEquals("Req 3.6: overlay 無しは isRead=false（サーバー値）", false, snapshot[1].isRead)
    }

    // ---- Req 2.3: markReadOnExternalOpen は ItemStateStore.markRead に委譲 -------------

    @Test
    fun `Req 2-3 markReadOnExternalOpen は ItemStateStore_markRead 経由でサーバー反映する`() = runTest {
        // Arrange
        val repo = RecordingItemDetailRepository()
        val store = newStore(repo)
        val viewModel = buildViewModel(
            searchRepository = StaticSearchRepository(flowOf(PagingData.empty())),
            itemStateStore = store,
        )

        // Act
        viewModel.markReadOnExternalOpen(itemId = "item-s1", currentIsRead = false)

        // Assert
        assertEquals(1, repo.updateStateCalls.size)
        assertEquals("item-s1", repo.updateStateCalls[0].itemId)
        assertEquals(true, repo.updateStateCalls[0].isRead)
        assertNull("isStarred は変更しない", repo.updateStateCalls[0].isStarred)
    }

    @Test
    fun `markReadOnExternalOpen は currentIsRead=true で冪等（API 再送しない）`() = runTest {
        // Arrange
        val repo = RecordingItemDetailRepository()
        val store = newStore(repo)
        val viewModel = buildViewModel(
            searchRepository = StaticSearchRepository(flowOf(PagingData.empty())),
            itemStateStore = store,
        )

        // Act
        viewModel.markReadOnExternalOpen(itemId = "item-r", currentIsRead = true)

        // Assert
        assertTrue("既読なら API を呼ばない（冪等）", repo.updateStateCalls.isEmpty())
    }

    // ---- Req 2.4: notifyExternalLinkFailed は OpenLinkFailed を流す ---------------------

    @Test
    fun `Req 2-4 notifyExternalLinkFailed で OpenLinkFailed が流れる`() = runTest {
        // Arrange
        val viewModel = buildViewModel(
            searchRepository = StaticSearchRepository(flowOf(PagingData.empty())),
        )
        val received = mutableListOf<SearchExternalLinkEvent>()
        val job = CoroutineScope(Dispatchers.Unconfined).launch {
            viewModel.externalLinkEvents.collect { received += it }
        }

        // Act
        viewModel.notifyExternalLinkFailed()

        // Assert
        assertEquals(1, received.size)
        assertEquals(SearchExternalLinkEvent.OpenLinkFailed, received[0])
        job.cancel()
    }

    // ---- Req 2.4: 既読化失敗は ItemStateStore.failures 経由で itemStateFailures に流れる ---

    @Test
    fun `Req 2-4 markReadOnExternalOpen の失敗は ItemStateStore_failures で通知される`() = runTest {
        // Arrange
        val error = FeedmanException(
            code = FeedmanException.CODE_NETWORK_ERROR,
            errorMessage = "オフライン",
        )
        val repo = RecordingItemDetailRepository(errorToThrow = error)
        val store = newStore(repo)
        val received = mutableListOf<ItemStateFailure>()
        val job = CoroutineScope(Dispatchers.Unconfined).launch {
            store.failures.collect { received += it }
        }
        val viewModel = buildViewModel(
            searchRepository = StaticSearchRepository(flowOf(PagingData.empty())),
            itemStateStore = store,
        )

        // Act
        viewModel.markReadOnExternalOpen(itemId = "x", currentIsRead = false)

        // Assert
        assertEquals(1, received.size)
        assertEquals("x", received[0].itemId)
        assertEquals(ItemStateFailure.Kind.Read, received[0].kind)
        assertEquals(
            "viewModel.itemStateFailures は同じ SharedFlow を再公開している",
            store.failures,
            viewModel.itemStateFailures,
        )
        job.cancel()
    }

    // ---- toggleStar の委譲（カード上での将来的なスタートグルや詳細シート経由を経由した同期）---

    @Test
    fun `toggleStar で ItemStateStore_setStarred 経由のサーバー反映が走る`() = runTest {
        // Arrange
        val repo = RecordingItemDetailRepository()
        val store = newStore(repo)
        val viewModel = buildViewModel(
            searchRepository = StaticSearchRepository(flowOf(PagingData.empty())),
            itemStateStore = store,
        )

        // Act
        viewModel.toggleStar(itemId = "ts", newState = true, baselineStarred = false)

        // Assert
        val starCalls = repo.updateStateCalls.filter { it.isStarred != null }
        assertEquals(1, starCalls.size)
        assertEquals("ts", starCalls[0].itemId)
        assertEquals(true, starCalls[0].isStarred)
        assertNull("isRead は変更しない", starCalls[0].isRead)
    }

    // ── helpers ──────────────────────────────────────────────────────

    private fun buildViewModel(
        searchRepository: SearchRepository,
        itemStateStore: ItemStateStore = newStore(NoopItemDetailRepository()),
    ): SearchViewModel = SearchViewModel(
        searchRepository = searchRepository,
        itemStateStore = itemStateStore,
    )

    private fun newStore(repo: ItemDetailRepository): ItemStateStore =
        ItemStateStore(repository = repo, scope = CoroutineScope(Dispatchers.Unconfined))

    private fun searchHit(
        id: String,
        title: String,
        isRead: Boolean = false,
        isStarred: Boolean = false,
    ): ItemSearchHit = ItemSearchHit(
        id = id,
        feedId = "feed-x",
        title = title,
        link = "https://example.com/$id",
        summary = "",
        publishedAt = "2026-06-12T11:30:00Z",
        isDateEstimated = false,
        hatebuCount = 0,
        feedTitle = "Feed X",
        faviconUrl = null,
        isRead = isRead,
        isStarred = isStarred,
    )

    private class StaticSearchRepository(
        private val source: Flow<PagingData<ItemSearchHit>>,
    ) : SearchRepository {
        override fun pagingData(query: String): Flow<PagingData<ItemSearchHit>> = source
    }

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
