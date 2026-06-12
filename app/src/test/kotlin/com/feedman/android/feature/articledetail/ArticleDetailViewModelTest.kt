package com.feedman.android.feature.articledetail

import app.cash.turbine.test
import com.feedman.android.core.data.ItemDetailRepository
import com.feedman.android.core.data.ItemStateStore
import com.feedman.android.core.model.ItemDetail
import com.feedman.android.core.network.FeedmanException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * [ArticleDetailViewModel] の単体テスト（Issue #36 / Issue #38）。
 *
 * リポジトリは fake で差し替え、状態遷移と楽観的更新を JVM 上で検証する。
 * ViewModel が公開する `StateFlow` / `SharedFlow` の挙動を Turbine で検証する。
 *
 * カバーする AC:
 * - Req 1.1: open(id) で Loading → Content に遷移
 * - Req 3.1 / Issue #38 Req 5.1: シート表示時に store.markRead 経由で isRead が即時 true
 * - Req 3.2 / Issue #38 Req 5.2: 既読化サーバーリクエストが発火する（未読のときのみ）
 * - Req 3.3: 失敗時に isRead を元に戻して MarkReadFailed イベントを流す
 * - Req 3.5 / Issue #38 Req 5.3: 既に既読の記事を開いた場合 updateState を呼ばない（冪等）
 * - Req 4.4 / Issue #38 Req 1.1: toggleStar で即時 isStarred トグル
 * - Req 4.5: スター更新失敗時にロールバック + StarUpdateFailed
 * - Req 4.3: markReadOnOpenExternal は未読のときのみ既読化、既読時は何もしない
 * - Req 6.2: 取得失敗で Error 状態に遷移
 * - Req 6.3: retry で Error → Loading で再取得
 * - Req 1.4: dismiss で Hidden に戻る
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArticleDetailViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── 取得成功 / 既読化フロー ───────────────────────────────────────────

    @Test
    fun `open は Loading_を経由して Content へ遷移し isRead を true にする_Req 1_1_3_1`() = runTest {
        // Arrange
        val detail = newDetail(id = "a1", isRead = false, isStarred = false)
        val repo = FakeRepository(getItemResult = Result.success(detail))
        val viewModel = newViewModel(repo)

        // Act + Assert
        viewModel.uiState.test {
            assertEquals(ArticleDetailUiState.Hidden, awaitItem())
            viewModel.open(itemId = "a1")
            // Loading が emit される場合 / されない場合がディスパッチャに依存するため両対応
            val loadingOrContent = awaitItem()
            val content = if (loadingOrContent is ArticleDetailUiState.Loading) awaitItem() else loadingOrContent
            // overlay 反映直後にもう一回 Content が来る可能性があるので最後の Content を取る
            val finalContent = drainUntilContentWithRead(this, content, expectedRead = true)
            assertEquals("a1", finalContent.detail.id)
            assertEquals(true, finalContent.isRead) // Req 3.1 / 3.4
            assertEquals(false, finalContent.isStarred)
            cancelAndIgnoreRemainingEvents()
        }
        // Req 3.2: 既読化リクエストが発火している
        assertEquals(1, repo.updateStateCalls.size)
        assertEquals("a1", repo.updateStateCalls[0].itemId)
        assertEquals(true, repo.updateStateCalls[0].isRead)
    }

    @Test
    fun `既に既読の記事を open しても updateState を再送しない_Req 3_5`() = runTest {
        // Arrange
        val detail = newDetail(id = "r1", isRead = true, isStarred = false)
        val repo = FakeRepository(getItemResult = Result.success(detail))
        val viewModel = newViewModel(repo)

        // Act
        viewModel.open(itemId = "r1")

        // Assert
        val state = viewModel.uiState.value
        assertTrue(state is ArticleDetailUiState.Content)
        assertEquals(true, (state as ArticleDetailUiState.Content).isRead)
        assertTrue("Req 3.5 — 既読再送なし", repo.updateStateCalls.isEmpty())
    }

    @Test
    fun `既読化サーバー反映失敗で isRead を false に戻し MarkReadFailed イベントを流す_Req 3_3`() = runTest {
        // Arrange
        val detail = newDetail(id = "x", isRead = false, isStarred = false)
        val repo = FakeRepository(
            getItemResult = Result.success(detail),
            updateStateError = FeedmanException(code = "X", errorMessage = "fail"),
        )
        val viewModel = newViewModel(repo)

        // Act + Assert
        viewModel.events.test {
            viewModel.open(itemId = "x")
            // 失敗イベントが流れることを確認
            assertEquals(ArticleDetailEvent.MarkReadFailed, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        // isRead はロールバック済み（overlay が baseline=false に戻っている）
        val state = viewModel.uiState.value
        assertTrue(state is ArticleDetailUiState.Content)
        assertEquals(false, (state as ArticleDetailUiState.Content).isRead)
    }

    // ── 取得失敗 / 再試行 ────────────────────────────────────────────────

    @Test
    fun `取得失敗で Error 状態へ遷移する_Req 6_2`() = runTest {
        // Arrange
        val repo = FakeRepository(
            getItemResult = Result.failure(FeedmanException(code = "X", errorMessage = "通信エラー")),
        )
        val viewModel = newViewModel(repo)

        // Act
        viewModel.open(itemId = "e1")

        // Assert
        val state = viewModel.uiState.value
        assertTrue(state is ArticleDetailUiState.Error)
        assertEquals("e1", (state as ArticleDetailUiState.Error).itemId)
        assertEquals("通信エラー", state.message)
        // 既読化 API は呼ばれない
        assertTrue(repo.updateStateCalls.isEmpty())
    }

    @Test
    fun `retry は Error 状態から再取得する_Req 6_3`() = runTest {
        // Arrange: 1 回目は失敗、2 回目は成功
        val repo = SequenceRepository(
            getItemResults = listOf(
                Result.failure(FeedmanException(code = "X", errorMessage = "fail")),
                Result.success(newDetail(id = "r2", isRead = true, isStarred = false)),
            ),
        )
        val viewModel = newViewModel(repo)

        // Act
        viewModel.open(itemId = "r2")
        assertTrue(viewModel.uiState.value is ArticleDetailUiState.Error)
        viewModel.retry()

        // Assert
        val state = viewModel.uiState.value
        assertTrue("retry 後に Content に遷移しているはず", state is ArticleDetailUiState.Content)
        assertEquals("r2", (state as ArticleDetailUiState.Content).detail.id)
    }

    // ── スター ──────────────────────────────────────────────────────────

    @Test
    fun `toggleStar で isStarred を即時トグルし updateState を呼ぶ_Req 4_4`() = runTest {
        // Arrange
        val detail = newDetail(id = "s1", isRead = true, isStarred = false)
        val repo = FakeRepository(getItemResult = Result.success(detail))
        val viewModel = newViewModel(repo)
        viewModel.open(itemId = "s1")

        // Act
        viewModel.toggleStar()

        // Assert
        val state = viewModel.uiState.value as ArticleDetailUiState.Content
        assertEquals(true, state.isStarred)
        val starCalls = repo.updateStateCalls.filter { it.isStarred != null }
        assertEquals(1, starCalls.size)
        assertEquals(true, starCalls[0].isStarred)
    }

    @Test
    fun `スター更新失敗時にロールバックして StarUpdateFailed イベントを流す_Req 4_5`() = runTest {
        // Arrange: getItem は成功、updateState は star 呼び出しのみ失敗させる
        val detail = newDetail(id = "s2", isRead = true, isStarred = false)
        val repo = FakeRepository(
            getItemResult = Result.success(detail),
            updateStateError = null, // 既読化は成功（既読なので呼ばれない）
        )
        val viewModel = newViewModel(repo)
        viewModel.open(itemId = "s2")

        // 次回 updateState を失敗させる
        repo.updateStateError = FeedmanException(code = "X", errorMessage = "fail")

        // Act + Assert
        viewModel.events.test {
            viewModel.toggleStar()
            assertEquals(ArticleDetailEvent.StarUpdateFailed, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        // ロールバック確認
        val state = viewModel.uiState.value as ArticleDetailUiState.Content
        assertEquals(false, state.isStarred)
    }

    // ── 外部リンク既読化 ────────────────────────────────────────────────

    @Test
    fun `markReadOnOpenExternal は未読のときのみ既読化する_Req 4_3`() = runTest {
        // Arrange
        val detail = newDetail(id = "o1", isRead = true, isStarred = false)
        val repo = FakeRepository(getItemResult = Result.success(detail))
        val viewModel = newViewModel(repo)
        viewModel.open(itemId = "o1")
        // open 時点で isRead=true。updateState 呼び出しは 0 件（Req 3.5）。
        assertTrue(repo.updateStateCalls.isEmpty())

        // Act
        viewModel.markReadOnOpenExternal()

        // Assert: 既読なので何もしない（Req 4.3 — 冪等）
        assertTrue("既読時は updateState 再送しない", repo.updateStateCalls.isEmpty())
    }

    @Test
    fun `markReadOnOpenExternal は未読 Content から既読化リクエストを発火する_Req 4_3`() = runTest {
        // Arrange: getItem は未読を返すが、最初の既読化は事前にロールバックで未読に戻す
        val detail = newDetail(id = "o2", isRead = false, isStarred = false)
        val repo = FakeRepository(
            getItemResult = Result.success(detail),
            updateStateError = FeedmanException(code = "X", errorMessage = "fail"),
        )
        val viewModel = newViewModel(repo)
        viewModel.open(itemId = "o2")
        // open 時の既読化が失敗してロールバックされ、isRead=false に戻る
        val rolledBack = viewModel.uiState.value as ArticleDetailUiState.Content
        assertEquals(false, rolledBack.isRead)
        // 次回 API は成功させる
        repo.updateStateError = null
        val openCallCount = repo.updateStateCalls.size

        // Act
        viewModel.markReadOnOpenExternal()

        // Assert
        val state = viewModel.uiState.value as ArticleDetailUiState.Content
        assertEquals(true, state.isRead)
        // 追加 1 件の既読化 API が発火した
        assertEquals(openCallCount + 1, repo.updateStateCalls.size)
    }

    // ── dismiss ─────────────────────────────────────────────────────────

    @Test
    fun `dismiss で Hidden に戻る_Req 1_4`() = runTest {
        // Arrange
        val detail = newDetail(id = "d1", isRead = true, isStarred = false)
        val repo = FakeRepository(getItemResult = Result.success(detail))
        val viewModel = newViewModel(repo)
        viewModel.open(itemId = "d1")
        assertTrue(viewModel.uiState.value is ArticleDetailUiState.Content)

        // Act
        viewModel.dismiss()

        // Assert
        assertEquals(ArticleDetailUiState.Hidden, viewModel.uiState.value)
    }

    // ── Issue #38: 画面間同期 ────────────────────────────────────────

    @Test
    fun `他画面で store_setStarred されたとき詳細シートの isStarred が更新される_Issue38 Req 4_1_4_2`() = runTest {
        // Arrange
        val detail = newDetail(id = "sync", isRead = true, isStarred = false)
        val repo = FakeRepository(getItemResult = Result.success(detail))
        val store = ItemStateStore(repository = repo, scope = CoroutineScope(Dispatchers.Unconfined))
        val viewModel = ArticleDetailViewModel(repository = repo, itemStateStore = store)
        viewModel.open(itemId = "sync")
        // open 後に Content / isStarred=false が反映されている
        assertEquals(false, (viewModel.uiState.value as ArticleDetailUiState.Content).isStarred)

        // Act: 別画面相当の経路で store にスター true を反映
        store.setStarred(itemId = "sync", isStarred = true, baselineStarred = false)

        // Assert: シートの uiState にも反映される（同じ store を共有しているため）
        val state = viewModel.uiState.value as ArticleDetailUiState.Content
        assertEquals(true, state.isStarred)
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private fun newViewModel(repo: ItemDetailRepository): ArticleDetailViewModel {
        val store = ItemStateStore(repository = repo, scope = CoroutineScope(Dispatchers.Unconfined))
        return ArticleDetailViewModel(repository = repo, itemStateStore = store)
    }

    /** Content への遷移を待ち、期待した isRead を持つ最新の Content を返す。 */
    private suspend fun drainUntilContentWithRead(
        scope: app.cash.turbine.ReceiveTurbine<ArticleDetailUiState>,
        first: ArticleDetailUiState,
        expectedRead: Boolean,
    ): ArticleDetailUiState.Content {
        var current = first as? ArticleDetailUiState.Content
            ?: throw AssertionError("Content への遷移を期待したが $first だった")
        // overlay 反映で追加 emit が来るかもしれないので、isRead=expected になるまで取る
        while (current.isRead != expectedRead) {
            val next = scope.awaitItem()
            if (next is ArticleDetailUiState.Content) current = next
        }
        return current
    }

    private fun newDetail(id: String, isRead: Boolean, isStarred: Boolean): ItemDetail =
        ItemDetail(
            id = id,
            feedId = "feed-x",
            title = "タイトル",
            link = "https://example.com/$id",
            summary = "サマリー",
            publishedAt = "2026-06-12T11:30:00Z",
            isDateEstimated = false,
            isRead = isRead,
            isStarred = isStarred,
            hatebuCount = 0,
            hatebuFetchedAt = null,
            content = "<p>本文</p>",
            author = "Author",
        )

    private data class UpdateCall(val itemId: String, val isRead: Boolean?, val isStarred: Boolean?)

    /**
     * 単一固定 result を返す fake。`updateStateError` を可変にしてテスト中に切り替え可能にする。
     */
    private class FakeRepository(
        private val getItemResult: Result<ItemDetail>,
        var updateStateError: FeedmanException? = null,
    ) : ItemDetailRepository {
        val updateStateCalls: MutableList<UpdateCall> = mutableListOf()

        override suspend fun getItem(itemId: String): ItemDetail =
            getItemResult.getOrThrow()

        override suspend fun updateState(itemId: String, isRead: Boolean?, isStarred: Boolean?) {
            updateStateCalls += UpdateCall(itemId = itemId, isRead = isRead, isStarred = isStarred)
            updateStateError?.let { throw it }
        }
    }

    /**
     * `getItem` の結果を呼び出し順に消費するシーケンス fake。retry テスト用。
     */
    private class SequenceRepository(
        private val getItemResults: List<Result<ItemDetail>>,
    ) : ItemDetailRepository {
        private var idx: Int = 0
        override suspend fun getItem(itemId: String): ItemDetail {
            if (idx >= getItemResults.size) fail("getItem の呼び出しが想定回数を超えた")
            val r = getItemResults[idx++]
            return r.getOrThrow()
        }

        override suspend fun updateState(itemId: String, isRead: Boolean?, isStarred: Boolean?) {
            // 既読化は成功させる
        }
    }
}
