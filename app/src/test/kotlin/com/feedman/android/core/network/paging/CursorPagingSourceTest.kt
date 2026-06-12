package com.feedman.android.core.network.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.testing.TestPager
import com.feedman.android.core.network.FeedmanException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * [CursorPagingSource] の AC 単位検証。
 *
 * SPEC §4.1 のカーソル envelope（`items` / `next_cursor` / `has_more`）に対する終端条件、
 * Issue #18 requirements.md Req 1〜4 の挙動を網羅する。
 */
class CursorPagingSourceTest {

    // ---- Req 1 / Req 2: 次キー解決と終端判定 --------------------------------

    @Test
    fun `load returns LoadResult Page carrying next_cursor when has_more is true (Req 1-1, 2-1)`() =
        runTest {
            // Arrange
            val loader = RecordingLoader(
                listOf(
                    CursorPage(items = listOf("a", "b"), nextCursor = "c2", hasMore = true),
                ),
            )
            val source = CursorPagingSource(loader)

            // Act
            val result = source.load(PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false))

            // Assert
            val page = result as PagingSource.LoadResult.Page
            assertEquals(listOf("a", "b"), page.data)
            assertNull("初回ページの prevKey は null（先頭ページ）", page.prevKey)
            assertEquals("c2", page.nextKey)
            assertEquals(listOf<String?>(null), loader.calls)
        }

    @Test
    fun `load forwards next_cursor opaquely to subsequent load (Req 1-1, 1-3)`() = runTest {
        // Arrange
        val opaque = "OPAQUE::eyJ0eXAiOiJ4In0.ABCDE-_=="
        val loader = RecordingLoader(
            listOf(
                CursorPage(items = listOf("a"), nextCursor = opaque, hasMore = true),
                CursorPage(items = listOf("b"), nextCursor = null, hasMore = false),
            ),
        )
        val source = CursorPagingSource(loader)

        // Act: refresh → append（cursor を渡す）
        val refresh = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 10, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page
        source.load(
            PagingSource.LoadParams.Append(key = refresh.nextKey!!, loadSize = 10, placeholdersEnabled = false),
        )

        // Assert: append 時にサーバーから受け取った不透明トークンをそのまま送出
        assertEquals(listOf(null, opaque), loader.calls)
    }

    @Test
    fun `load with null key on Refresh means initial fetch with no cursor (Req 1-2)`() = runTest {
        // Arrange
        val loader = RecordingLoader(
            listOf(CursorPage(items = listOf("a"), nextCursor = null, hasMore = false)),
        )
        val source = CursorPagingSource(loader)

        // Act
        source.load(PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false))

        // Assert
        assertEquals("初回はカーソル未指定（null）", listOf<String?>(null), loader.calls)
    }

    @Test
    fun `load returns terminal nextKey when has_more is false (Req 2-1)`() = runTest {
        // Arrange
        val loader = RecordingLoader(
            listOf(
                CursorPage(
                    items = listOf("a"),
                    nextCursor = "still-here-but-ignored",
                    hasMore = false,
                ),
            ),
        )
        val source = CursorPagingSource(loader)

        // Act
        val result = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false),
        )

        // Assert
        val page = result as PagingSource.LoadResult.Page
        assertNull(
            "has_more=false なら nextKey は null（next_cursor が非 null でも優先）",
            page.nextKey,
        )
    }

    @Test
    fun `load returns terminal nextKey when next_cursor is null (Req 2-2)`() = runTest {
        // Arrange
        val loader = RecordingLoader(
            listOf(CursorPage(items = listOf("a"), nextCursor = null, hasMore = true)),
        )
        val source = CursorPagingSource(loader)

        // Act
        val result = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false),
        )

        // Assert: next_cursor=null なら has_more=true でも終端扱い（保守側に倒す）
        val page = result as PagingSource.LoadResult.Page
        assertNull(page.nextKey)
    }

    @Test
    fun `load returns terminal nextKey when next_cursor is empty string (Req 2-2)`() = runTest {
        // Arrange
        val loader = RecordingLoader(
            listOf(CursorPage(items = listOf("a"), nextCursor = "", hasMore = true)),
        )
        val source = CursorPagingSource(loader)

        // Act
        val result = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false),
        )

        // Assert
        val page = result as PagingSource.LoadResult.Page
        assertNull("空文字列カーソルも終端", page.nextKey)
    }

    @Test
    fun `terminal page is not followed by additional load via TestPager (Req 2-3)`() = runTest {
        // Arrange
        val loader = RecordingLoader(
            listOf(
                CursorPage(items = listOf("a"), nextCursor = "c2", hasMore = true),
                CursorPage(items = listOf("b"), nextCursor = null, hasMore = false),
            ),
        )
        val source = CursorPagingSource(loader)
        val pager = TestPager(config = androidx.paging.PagingConfig(pageSize = 10), pagingSource = source)

        // Act: refresh → append → さらに append（終端なので no-op）
        pager.refresh()
        pager.append()
        val third = pager.append()

        // Assert
        assertNull("終端到達後の append は null（追加ロード発行されない）", third)
        assertEquals("loader 呼び出しは 2 回まで（refresh + 1 回の append）", 2, loader.calls.size)
    }

    // ---- Req 3: エラー伝播と再試行 ------------------------------------------

    @Test
    fun `initial FeedmanException is exposed as LoadResult Error (Req 3-1)`() = runTest {
        // Arrange
        val fe = FeedmanException(code = "UNAUTHORIZED", errorMessage = "認証エラー", httpStatus = 401)
        val loader = ScriptedLoader(listOf(LoaderStep.Throw(fe)))
        val source = CursorPagingSource(loader)

        // Act
        val result = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false),
        )

        // Assert
        val error = result as PagingSource.LoadResult.Error
        assertSame(fe, error.throwable)
    }

    @Test
    fun `initial IOException is exposed as LoadResult Error (Req 3-1)`() = runTest {
        // Arrange
        val io = IOException("socket reset")
        val loader = ScriptedLoader(listOf(LoaderStep.Throw(io)))
        val source = CursorPagingSource(loader)

        // Act
        val result = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false),
        )

        // Assert
        val error = result as PagingSource.LoadResult.Error
        assertSame(io, error.throwable)
    }

    @Test
    fun `append FeedmanException is exposed as LoadResult Error without dropping loaded pages (Req 3-2)`() =
        runTest {
            // Arrange
            val fe = FeedmanException(code = "RATE_LIMITED", errorMessage = "rate limited", httpStatus = 429)
            val loader = ScriptedLoader(
                listOf(
                    LoaderStep.Return(CursorPage(items = listOf("a"), nextCursor = "c2", hasMore = true)),
                    LoaderStep.Throw(fe),
                ),
            )
            val source = CursorPagingSource(loader)
            val pager = TestPager(config = androidx.paging.PagingConfig(pageSize = 10), pagingSource = source)

            // Act
            val refresh = pager.refresh() as PagingSource.LoadResult.Page
            val appendResult = pager.append()

            // Assert: refresh 結果は維持される（pager.getPages() に含まれる）
            assertEquals(listOf("a"), refresh.data)
            val pages = pager.getPages()
            assertEquals(1, pages.size)
            assertEquals(listOf("a"), pages[0].data)
            // append は Error として露出する
            val error = appendResult as PagingSource.LoadResult.Error
            assertSame(fe, error.throwable)
        }

    @Test
    fun `retry re-issues request with the same cursor after append failure (Req 3-3)`() = runTest {
        // Arrange
        val fe = FeedmanException(code = "RATE_LIMITED", errorMessage = "rate limited", httpStatus = 429)
        val successPage = CursorPage(items = listOf("b"), nextCursor = null, hasMore = false)
        val loader = ScriptedLoader(
            listOf(
                LoaderStep.Return(CursorPage(items = listOf("a"), nextCursor = "c2", hasMore = true)),
                LoaderStep.Throw(fe),
                LoaderStep.Return(successPage),
            ),
        )
        val source = CursorPagingSource(loader)

        // Act: refresh → append（fail）→ 同じカーソルで再 append（success）
        val refresh = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 10, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page
        val failedKey = refresh.nextKey!!
        val failed = source.load(
            PagingSource.LoadParams.Append(key = failedKey, loadSize = 10, placeholdersEnabled = false),
        )
        val retried = source.load(
            PagingSource.LoadParams.Append(key = failedKey, loadSize = 10, placeholdersEnabled = false),
        )

        // Assert
        assertTrue(failed is PagingSource.LoadResult.Error)
        val retriedPage = retried as PagingSource.LoadResult.Page
        assertEquals(listOf("b"), retriedPage.data)
        // loader には null（refresh）, "c2"（失敗）, "c2"（retry）の順で 3 回呼ばれた
        assertEquals(listOf<String?>(null, "c2", "c2"), loader.calls)
    }

    // ---- Req 4: リフレッシュ挙動 --------------------------------------------

    @Test
    fun `refresh starts from initial cursor null (Req 4-1)`() = runTest {
        // Arrange
        val loader = ScriptedLoader(
            listOf(
                LoaderStep.Return(CursorPage(items = listOf("a"), nextCursor = "c2", hasMore = true)),
                LoaderStep.Return(CursorPage(items = listOf("a'"), nextCursor = "c2'", hasMore = true)),
            ),
        )
        val source = CursorPagingSource(loader)

        // Act: 1 回目の refresh → 2 回目の refresh
        source.load(PagingSource.LoadParams.Refresh(key = null, loadSize = 10, placeholdersEnabled = false))
        // Paging 側からの refresh も key=null で呼ばれる契約だが、念のため明示
        source.load(PagingSource.LoadParams.Refresh(key = null, loadSize = 10, placeholdersEnabled = false))

        // Assert: 2 回とも cursor=null から再開（先頭ページから取得）
        assertEquals(listOf<String?>(null, null), loader.calls)
    }

    @Test
    fun `getRefreshKey always returns null so refresh restarts from the top (Req 4-1)`() {
        // Arrange
        val loader = RecordingLoader(emptyList())
        val source = CursorPagingSource(loader)
        val state = PagingState<String, String>(
            pages = emptyList(),
            anchorPosition = 42,
            config = androidx.paging.PagingConfig(pageSize = 10),
            leadingPlaceholderCount = 0,
        )

        // Act
        val key = source.getRefreshKey(state)

        // Assert
        assertNull("カーソル方式は anchor から前ページを推測できないため refresh は常に先頭", key)
    }

    @Test
    fun `refresh after successful load applies same terminal rules as Req 1 and 2 (Req 4-2)`() =
        runTest {
            // Arrange
            val loader = ScriptedLoader(
                listOf(
                    LoaderStep.Return(CursorPage(items = listOf("a"), nextCursor = "c2", hasMore = true)),
                    // refresh 後の先頭ページは終端
                    LoaderStep.Return(CursorPage(items = listOf("z"), nextCursor = "", hasMore = true)),
                ),
            )
            val source = CursorPagingSource(loader)

            // Act
            source.load(PagingSource.LoadParams.Refresh(key = null, loadSize = 10, placeholdersEnabled = false))
            val refreshed = source.load(
                PagingSource.LoadParams.Refresh(key = null, loadSize = 10, placeholdersEnabled = false),
            )

            // Assert: 空文字列カーソルは Req 2-2 と同じく終端扱い
            val page = refreshed as PagingSource.LoadResult.Page
            assertEquals(listOf("z"), page.data)
            assertNull(page.nextKey)
        }

    // ---- Req 5: ロジック差し替え可能性 --------------------------------------

    @Test
    fun `loader is the only injection point and source is endpoint-agnostic (Req 5-1)`() = runTest {
        // Arrange: 任意のラムダを loader として渡せること自体を検証
        val captured = mutableListOf<String?>()
        val loader: suspend (String?) -> CursorPage<Int> = { cursor ->
            captured += cursor
            CursorPage(items = listOf(1, 2, 3), nextCursor = "next", hasMore = true)
        }
        val source = CursorPagingSource(loader)

        // Act
        val result = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 10, placeholdersEnabled = false),
        )

        // Assert
        val page = result as PagingSource.LoadResult.Page
        assertEquals(listOf(1, 2, 3), page.data)
        assertEquals(listOf<String?>(null), captured)
        assertNotNull(page.nextKey)
    }

    // ---- Test helpers --------------------------------------------------------

    /**
     * 呼び出しごとの cursor を記録しつつ、用意済みページを順に返すローダ。
     */
    private class RecordingLoader(
        private val pages: List<CursorPage<String>>,
    ) : suspend (String?) -> CursorPage<String> {
        val calls: MutableList<String?> = mutableListOf()
        override suspend operator fun invoke(cursor: String?): CursorPage<String> {
            val index = calls.size
            calls += cursor
            return pages[index]
        }
    }

    /**
     * 各ステップごとに「成功ページ返却」「例外 throw」を切り替えるローダ。
     */
    private class ScriptedLoader(
        private val steps: List<LoaderStep>,
    ) : suspend (String?) -> CursorPage<String> {
        val calls: MutableList<String?> = mutableListOf()
        override suspend operator fun invoke(cursor: String?): CursorPage<String> {
            val index = calls.size
            calls += cursor
            return when (val step = steps[index]) {
                is LoaderStep.Return -> step.page
                is LoaderStep.Throw -> throw step.throwable
            }
        }
    }

    private sealed interface LoaderStep {
        data class Return(val page: CursorPage<String>) : LoaderStep
        data class Throw(val throwable: Throwable) : LoaderStep
    }
}
