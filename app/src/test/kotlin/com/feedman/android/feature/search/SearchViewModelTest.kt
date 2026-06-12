package com.feedman.android.feature.search

import androidx.paging.PagingData
import app.cash.turbine.test
import com.feedman.android.core.data.SearchRepository
import com.feedman.android.core.model.ItemSearchHit
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [SearchViewModel] の AC 単位検証（Issue #47 / NFR 2.3 / NFR 2.4）。
 *
 * 検索送信前は [SearchRepository.pagingData] を一切呼び出さないこと（Req 2.1 / 3.2 / NFR 1.2）、
 * サジェストチップから検索開始までの導線（Req 2.3 / 2.4）、キーワード変更による新規 Pager
 * 起動（Req 5.4）、入力クリアでの空クエリ復帰（Req 1.5）を直接観測する。
 *
 * Repository は同一モジュール内のフェイクを使い、`pagingData(query)` 呼び出しを `Iquery` に
 * 記録する。これにより「呼ばれた / 呼ばれていない」「どのキーワードで呼ばれたか」を機械
 * 検証できる。Pager 出力本体（PagingData の中身）は本 VM テストの対象外（mapper /
 * repository テストで担保 / NFR 2.2）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @Before
    fun setUpMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDownMainDispatcher() {
        Dispatchers.resetMain()
    }

    /**
     * フェイク [SearchRepository]。`pagingData(query)` 呼び出しを `queries` に記録する。
     * `cardPagingData` 経由で実際に collect されるまで本クラスの `pagingData` は呼ばれない
     * （ViewModel が `flatMapLatest` + `cachedIn` 経由で購読する）。
     */
    private class RecordingSearchRepository : SearchRepository {
        val queries: MutableList<String> = mutableListOf()

        override fun pagingData(query: String): Flow<PagingData<ItemSearchHit>> {
            queries += query
            return flowOf(PagingData.empty())
        }
    }

    // ---- Req 2.1 / 3.2 / NFR 1.2: 初期状態で repository を呼ばない -----------

    @Test
    fun `initial state has empty query and null submittedQuery`() = runTest {
        // Arrange
        val repo = RecordingSearchRepository()
        val vm = SearchViewModel(repo)

        // Act / Assert
        assertEquals("", vm.queryInput.value)
        assertNull(vm.submittedQuery.value)
        assertTrue("初期状態では repository は呼ばれない", repo.queries.isEmpty())
    }

    // ---- Req 2.1 / 3.2: queryInput 変更だけでは repository を呼ばない -------

    @Test
    fun `onQueryChanged does not invoke repository`() = runTest {
        // Arrange
        val repo = RecordingSearchRepository()
        val vm = SearchViewModel(repo)

        // Act
        vm.cardPagingData.test {
            vm.onQueryChanged("kotlin")
            // queryInput 変更だけでは新しい Pager は流れない（空クエリ＝null）
            assertEquals("kotlin", vm.queryInput.value)
            cancelAndIgnoreRemainingEvents()
        }

        // Assert
        assertTrue(
            "Req 2.1 / 3.2 / NFR 1.2: queryInput 変更だけでは repository が呼ばれない",
            repo.queries.isEmpty(),
        )
        assertNull(
            "未送信なので submittedQuery は null のまま（空クエリ表示維持 / Req 2.1）",
            vm.submittedQuery.value,
        )
    }

    // ---- Req 3.1 / 3.2: submit は前後空白除去後に空なら何もしない ----------

    @Test
    fun `submit with whitespace only input does not invoke repository`() = runTest {
        // Arrange
        val repo = RecordingSearchRepository()
        val vm = SearchViewModel(repo)

        // Act
        vm.cardPagingData.test {
            vm.onQueryChanged("   ")
            vm.submit()
            cancelAndIgnoreRemainingEvents()
        }

        // Assert
        assertTrue(
            "Req 3.1 / 3.2: 空白のみのキーワードを送信しても repository は呼ばれない",
            repo.queries.isEmpty(),
        )
        assertNull("Req 3.2: submittedQuery は更新されない", vm.submittedQuery.value)
    }

    // ---- Req 3.1 / 3.3: submit は前後空白除去後の値で repository を呼ぶ ----

    @Test
    fun `submit trims and triggers repository with normalised query`() = runTest {
        // Arrange
        val repo = RecordingSearchRepository()
        val vm = SearchViewModel(repo)

        // Act
        vm.cardPagingData.test {
            vm.onQueryChanged("  kotlin  ")
            vm.submit()
            // cardPagingData は flatMapLatest + cachedIn で起動する。最低 1 件 emit する。
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        // Assert
        assertEquals(
            "Req 3.1: 前後空白を除去した値で repository を呼ぶ",
            listOf("kotlin"),
            repo.queries,
        )
        assertEquals("kotlin", vm.submittedQuery.value)
    }

    // ---- Req 5.4: キーワード変更で新しい Pager が生成される -----------------

    @Test
    fun `submitting different query invokes repository again with new keyword`() = runTest {
        // Arrange
        val repo = RecordingSearchRepository()
        val vm = SearchViewModel(repo)

        // Act
        vm.cardPagingData.test {
            vm.onQueryChanged("kotlin")
            vm.submit()
            awaitItem()
            vm.onQueryChanged("rust")
            vm.submit()
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        // Assert
        assertEquals(
            "Req 5.4: 新しいキーワードで新しい Pager が生成され、repository が再度呼ばれる",
            listOf("kotlin", "rust"),
            repo.queries,
        )
    }

    // ---- Req 2.3 / 2.4: サジェストチップ選択で入力欄投入 + 検索開始 --------

    @Test
    fun `selectSuggestion puts text and triggers search`() = runTest {
        // Arrange
        val repo = RecordingSearchRepository()
        val vm = SearchViewModel(repo)

        // Act
        vm.cardPagingData.test {
            vm.selectSuggestion("Kubernetes")
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        // Assert
        assertEquals(
            "Req 2.3: 当該チップの文字列を入力欄に投入する",
            "Kubernetes",
            vm.queryInput.value,
        )
        assertEquals(
            "Req 2.4: チップ選択で送信フローと同じ規則で検索を開始する",
            listOf("Kubernetes"),
            repo.queries,
        )
    }

    // ---- Req 1.5: クリアで入力欄を空、submittedQuery を null に戻す ---------

    @Test
    fun `clear empties input and reverts to null submittedQuery`() = runTest {
        // Arrange
        val repo = RecordingSearchRepository()
        val vm = SearchViewModel(repo)

        // Act: 1 回検索を確定してから clear
        vm.cardPagingData.test {
            vm.onQueryChanged("kotlin")
            vm.submit()
            awaitItem()
            vm.clear()
            cancelAndIgnoreRemainingEvents()
        }

        // Assert
        assertEquals(
            "Req 1.5: クリアで入力欄が空に戻る",
            "",
            vm.queryInput.value,
        )
        assertNull(
            "Req 1.5 / 2.1: クリアで submittedQuery が null に戻り、空クエリ時の表示に切替えられる",
            vm.submittedQuery.value,
        )
    }

    // ---- NFR 2.3 (boundary): 静的サジェスト候補が公開されている -------------

    @Test
    fun `SUGGESTIONS list is non-empty and matches FMSearchScreen prototype`() {
        // Req 2.2: プロトタイプ FMSearchScreen に準じたサジェストチップ群
        assertEquals(
            listOf("Go", "Kubernetes", "OpenAI", "TypeScript", "Rust"),
            SearchViewModel.SUGGESTIONS,
        )
    }
}
