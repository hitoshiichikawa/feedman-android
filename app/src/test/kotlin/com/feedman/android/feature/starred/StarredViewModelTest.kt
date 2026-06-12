package com.feedman.android.feature.starred

import androidx.paging.PagingData
import androidx.paging.testing.asSnapshot
import com.feedman.android.core.data.ItemDetailRepository
import com.feedman.android.core.data.ItemStateFailure
import com.feedman.android.core.data.ItemStateStore
import com.feedman.android.core.data.StarredItemsRepository
import com.feedman.android.core.model.ItemDetail
import com.feedman.android.core.model.StarredItemSummary
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
 * [StarredViewModel] の AC 単位検証（Issue #46）。
 *
 * - cardPagingData は StarredItemsRepository の items を ArticleCardModel に変換し、
 *   ItemStateStore.overlays と combine する（Req 1.3 / 1.4 / 5.1 / 5.2）
 * - スター解除時（overlay isStarred=false）でも当該カードはリストから除去されず、
 *   `isStarred=false` に切り替わるだけ（Req 5.3）
 * - リフレッシュ後の除去（Req 5.4）はサーバー側 filter で成立するため、本 VM では
 *   サーバー応答が新しい items を返した場合に overlay 適用後でも当該 ID が含まれる/含まれない
 *   ことを検証する
 * - toggleStar が ItemStateStore.setStarred を呼ぶこと
 * - failures が ItemStateStore.failures を再公開すること（Req 5.5）
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StarredViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---- Req 1.3 / 1.4: items → ArticleCardModel + feed_title 転写 -----------

    @Test
    fun `Req 1-3 1-4 cardPagingData は items を ArticleCardModel に変換し feed_title をソース表示に伝える`() = runTest {
        // Arrange
        val items = listOf(
            starredItem(id = "a", title = "A", feedTitle = "Android Developers"),
            starredItem(id = "b", title = "B", feedTitle = "Kotlin Blog"),
        )
        val viewModel = buildViewModel(
            starredItemsRepository = FakeStarredItemsRepository(flowOf(PagingData.from(items))),
        )

        // Act
        val snapshot: List<ArticleCardModel> = viewModel.cardPagingDataForTest().asSnapshot()

        // Assert (Req 1.3 / 1.4)
        assertEquals(2, snapshot.size)
        assertEquals("a", snapshot[0].id)
        assertEquals("A", snapshot[0].title)
        assertEquals("Android Developers", snapshot[0].feedTitle)
        assertEquals("Kotlin Blog", snapshot[1].feedTitle)
    }

    @Test
    fun `空のスター一覧も PagingData として伝播する_Req 3_1 の前提`() = runTest {
        // Arrange
        val viewModel = buildViewModel(
            starredItemsRepository = FakeStarredItemsRepository(flowOf(PagingData.empty())),
        )

        // Act
        val snapshot = viewModel.cardPagingDataForTest().asSnapshot()

        // Assert (空状態は UI 側で 0 件として扱う / Req 3.1 の前提)
        assertTrue(snapshot.isEmpty())
    }

    // ---- Req 5.1 / 5.2: overlay 即時反映 -------------------------------------

    @Test
    fun `Req 5-1 ItemStateStore overlay 値はサーバー値より優先される（即時反映）`() = runTest {
        // Arrange: サーバーは isStarred=true を返すが overlay で false（解除）に倒す
        val item = starredItem(id = "ov", title = "Overlay", feedTitle = "F").copy(isStarred = true)
        val repo = RecordingItemDetailRepository()
        val store = newStore(repo)
        // overlay を false 上書き（スター解除）。baseline=true（直前のサーバー値）
        store.setStarred(itemId = "ov", isStarred = false, baselineStarred = true)

        val viewModel = buildViewModel(
            starredItemsRepository = FakeStarredItemsRepository(flowOf(PagingData.from(listOf(item)))),
            itemStateStore = store,
        )

        // Act
        val snapshot = viewModel.cardPagingDataForTest().asSnapshot()

        // Assert
        val card = snapshot.single()
        assertEquals("ov", card.id)
        assertEquals(
            "Req 5.1: overlay の isStarred=false がサーバー値 true より優先される",
            false,
            card.isStarred,
        )
    }

    @Test
    fun `Req 5-2 詳細シート由来の overlay 更新も同じストアを経由して即時反映される`() = runTest {
        // Arrange: スター一覧の同一記事に対して別経路（= 詳細シート相当のテストコード）から
        // overlay を更新したケースを再現。ItemStateStore は singleton なので、同じ store
        // インスタンスを経由するだけで両画面の表示が同期する。
        val item = starredItem(id = "sheet", title = "Sheet", feedTitle = "F").copy(isStarred = true)
        val repo = RecordingItemDetailRepository()
        val store = newStore(repo)

        // 「詳細シート側」が overlay を解除に倒したと仮定
        store.setStarred(itemId = "sheet", isStarred = false, baselineStarred = true)

        val viewModel = buildViewModel(
            starredItemsRepository = FakeStarredItemsRepository(flowOf(PagingData.from(listOf(item)))),
            itemStateStore = store,
        )

        // Act
        val snapshot = viewModel.cardPagingDataForTest().asSnapshot()

        // Assert (Req 5.2)
        assertEquals(false, snapshot.single().isStarred)
    }

    // ---- Req 5.3: スター解除時の残置（リストから除去せず outline に） --------

    @Test
    fun `Req 5-3 スター解除（overlay isStarred=false）でも当該行はリストから除去されない`() = runTest {
        // Arrange: 3 件のうち真ん中をスター解除
        val items = listOf(
            starredItem(id = "k1", title = "K1", feedTitle = "F").copy(isStarred = true),
            starredItem(id = "k2", title = "K2", feedTitle = "F").copy(isStarred = true),
            starredItem(id = "k3", title = "K3", feedTitle = "F").copy(isStarred = true),
        )
        val repo = RecordingItemDetailRepository()
        val store = newStore(repo)
        store.setStarred(itemId = "k2", isStarred = false, baselineStarred = true)

        val viewModel = buildViewModel(
            starredItemsRepository = FakeStarredItemsRepository(flowOf(PagingData.from(items))),
            itemStateStore = store,
        )

        // Act
        val snapshot = viewModel.cardPagingDataForTest().asSnapshot()

        // Assert
        assertEquals(
            "Req 5.3: スター解除した記事もリストから除去されず 3 件全件が残る",
            3,
            snapshot.size,
        )
        assertEquals("k1", snapshot[0].id)
        assertEquals("k2", snapshot[1].id) // 残置
        assertEquals(true, snapshot[0].isStarred)
        assertEquals(
            "Req 5.3: 解除済み記事は isStarred=false で outline 表示になる",
            false,
            snapshot[1].isStarred,
        )
        assertEquals(true, snapshot[2].isStarred)
    }

    // ---- Req 5.4: リフレッシュ後の除去（サーバー filter で自動成立） --------

    @Test
    fun `Req 5-4 リフレッシュ後にサーバーが解除済みを除外したレスポンスを返せば当該行は表示されない`() = runTest {
        // Arrange: サーバーが「リフレッシュ後の最新状態」として k2 を含まない 2 件のみを返す
        // 状況を再現する。本 VM のフィルタリング機構ではなく、サーバー側の filter で除去が
        // 成立することを担保する（VM が独自に「解除確定済み」を覚えて削除する責務を負わないこと）。
        val refreshedItems = listOf(
            starredItem(id = "k1", title = "K1", feedTitle = "F").copy(isStarred = true),
            starredItem(id = "k3", title = "K3", feedTitle = "F").copy(isStarred = true),
        )
        val repo = RecordingItemDetailRepository()
        val store = newStore(repo)
        // 直前まで overlay には k2 の解除が残っていたとする
        store.setStarred(itemId = "k2", isStarred = false, baselineStarred = true)

        val viewModel = buildViewModel(
            starredItemsRepository = FakeStarredItemsRepository(flowOf(PagingData.from(refreshedItems))),
            itemStateStore = store,
        )

        // Act
        val snapshot = viewModel.cardPagingDataForTest().asSnapshot()

        // Assert
        assertEquals("Req 5.4: リフレッシュ後は k2 が消えて 2 件", 2, snapshot.size)
        assertEquals("k1", snapshot[0].id)
        assertEquals("k3", snapshot[1].id)
    }

    // ---- Req 5.5: ロールバック時の表示復元 ----------------------------------

    @Test
    fun `Req 5-5 楽観的更新のサーバー反映が失敗するとロールバックで isStarred が直前値に戻る`() = runTest {
        // Arrange: setStarred を呼ぶと ItemDetailRepository が例外を投げ、overlay がロールバックされる
        val item = starredItem(id = "rb", title = "Rollback", feedTitle = "F").copy(isStarred = true)
        val error = FeedmanException(code = "INTERNAL", errorMessage = "サーバーエラー")
        val repo = RecordingItemDetailRepository(errorToThrow = error)
        val store = newStore(repo)
        val received = mutableListOf<ItemStateFailure>()
        val job = CoroutineScope(Dispatchers.Unconfined).launch {
            store.failures.collect { received += it }
        }
        val viewModel = buildViewModel(
            starredItemsRepository = FakeStarredItemsRepository(flowOf(PagingData.from(listOf(item)))),
            itemStateStore = store,
        )

        // Act: スター解除を試みる → サーバーが失敗 → overlay が baseline=true に巻き戻る
        viewModel.toggleStar(itemId = "rb", newState = false, baselineStarred = true)

        // Assert
        // 1) failures が流れた
        assertEquals(1, received.size)
        assertEquals("rb", received[0].itemId)
        assertEquals(ItemStateFailure.Kind.Star, received[0].kind)
        // 2) overlay は baseline=true に戻っているため、合成後の表示も isStarred=true に復元される
        val snapshot = viewModel.cardPagingDataForTest().asSnapshot()
        assertEquals(true, snapshot.single().isStarred)
        // 3) viewModel.itemStateFailures は同じ SharedFlow を再公開している
        assertEquals(store.failures, viewModel.itemStateFailures)
        job.cancel()
    }

    // ---- toggleStar / ItemStateStore 委譲 -----------------------------------

    @Test
    fun `toggleStar で ItemStateStore_setStarred 経由のサーバー反映が走る`() = runTest {
        // Arrange
        val repo = RecordingItemDetailRepository()
        val store = newStore(repo)
        val viewModel = buildViewModel(
            starredItemsRepository = FakeStarredItemsRepository(flowOf(PagingData.empty())),
            itemStateStore = store,
        )

        // Act
        viewModel.toggleStar(itemId = "t1", newState = false, baselineStarred = true)

        // Assert
        val starCalls = repo.updateStateCalls.filter { it.isStarred != null }
        assertEquals(1, starCalls.size)
        assertEquals("t1", starCalls[0].itemId)
        assertEquals(false, starCalls[0].isStarred)
        assertNull("isRead は変更されない", starCalls[0].isRead)
    }

    // ── helpers ──────────────────────────────────────────────────────

    private fun buildViewModel(
        starredItemsRepository: StarredItemsRepository,
        itemStateStore: ItemStateStore = newStore(NoopItemDetailRepository()),
    ): StarredViewModel = StarredViewModel(
        starredItemsRepository = starredItemsRepository,
        itemStateStore = itemStateStore,
    )

    private fun newStore(repo: ItemDetailRepository): ItemStateStore =
        ItemStateStore(repository = repo, scope = CoroutineScope(Dispatchers.Unconfined))

    private fun starredItem(id: String, title: String, feedTitle: String): StarredItemSummary =
        StarredItemSummary(
            id = id,
            feedId = "feed-x",
            title = title,
            link = "https://example.com/$id",
            summary = "",
            publishedAt = "2026-06-12T11:30:00Z",
            isDateEstimated = false,
            isRead = false,
            isStarred = true,
            hatebuCount = 0,
            hatebuFetchedAt = null,
            feedTitle = feedTitle,
        )

    private class FakeStarredItemsRepository(
        private val source: Flow<PagingData<StarredItemSummary>>,
    ) : StarredItemsRepository {
        override fun pagingData(): Flow<PagingData<StarredItemSummary>> = source
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
