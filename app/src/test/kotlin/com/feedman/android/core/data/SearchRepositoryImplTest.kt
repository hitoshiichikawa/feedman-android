package com.feedman.android.core.data

import androidx.paging.PagingSource
import androidx.paging.testing.TestPager
import com.feedman.android.core.model.FixtureLoader
import com.feedman.android.core.network.ApiClientFactory
import com.feedman.android.core.network.FeedmanException
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * [SearchRepositoryImpl] の AC 単位検証（Issue #47 requirements.md）。
 *
 * MockWebServer で実 HTTP 経路を再現し、`/api/items/search?q=&scope=global` の
 * クエリ受け渡し・カーソル搬送・終端判定・エラー透過・キーワード再起動・
 * [com.feedman.android.core.model.ItemSearchHit] の nullable フィールド伝達を検証する。
 * Retrofit / OkHttp / kotlinx.serialization は実物を使い、`FeedmanApi` をモックしない
 * （CLAUDE.md テスト規約）。
 */
class SearchRepositoryImplTest {

    private lateinit var server: MockWebServer
    private lateinit var baseUrl: String

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        baseUrl = server.url("/").toString()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun newRepository(): SearchRepositoryImpl {
        val api = ApiClientFactory.create(baseUrl = baseUrl)
        return SearchRepositoryImpl(api)
    }

    // ---- Req 3.3: 初回ロードは q + scope=global + cursor 未指定で先頭ページ ---

    @Test
    fun `Req 3-3 initial load sends q and scope=global without cursor`() = runTest {
        // Arrange
        val body = FixtureLoader.load("search_page_has_more.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val repo = newRepository()
        val source = repo.newPagingSource(query = "kotlin")

        // Act
        source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        )

        // Assert
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/api/items/search", recorded.requestUrl?.encodedPath)
        assertEquals("kotlin", recorded.requestUrl?.queryParameter("q"))
        assertEquals(
            "Req 3.3 / 7.2: scope は常に global を送信する",
            "global",
            recorded.requestUrl?.queryParameter("scope"),
        )
        assertEquals("50", recorded.requestUrl?.queryParameter("limit"))
        assertNull(
            "Req 3.3: 初回ロードでは cursor クエリが付かない（Retrofit は null Query を送信しない）",
            recorded.requestUrl?.queryParameter("cursor"),
        )
    }

    // ---- Req 5.1 / 5.5: 後続ロードは前ページの next_cursor + 同一 q を搬送 -----

    @Test
    fun `Req 5-1 and 5-5 subsequent load forwards next_cursor and preserves q`() = runTest {
        // Arrange
        val first = FixtureLoader.load("search_page_has_more.json")
        val second = FixtureLoader.load("search_page_second.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(first))
        server.enqueue(MockResponse().setResponseCode(200).setBody(second))
        val repo = newRepository()
        val source = repo.newPagingSource(query = "android")

        // Act
        val firstResult = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page
        val nextKey = firstResult.nextKey!!
        source.load(
            PagingSource.LoadParams.Append(key = nextKey, loadSize = 50, placeholdersEnabled = false),
        )

        // Assert
        server.takeRequest() // skip initial
        val appendRequest = server.takeRequest()
        assertEquals(
            "Req 5.1: 次ページ要求では前ページの next_cursor がそのままクエリに乗る",
            "2026-06-11T08:00:00Z:01HSEARCH00000000000000001B",
            appendRequest.requestUrl?.queryParameter("cursor"),
        )
        assertEquals(
            "Req 5.5: 後続ページ要求でも開始時のキーワードを保持する",
            "android",
            appendRequest.requestUrl?.queryParameter("q"),
        )
        assertEquals(
            "Req 7.2: scope は後続ページも global 固定",
            "global",
            appendRequest.requestUrl?.queryParameter("scope"),
        )
        assertEquals("/api/items/search", appendRequest.requestUrl?.encodedPath)
    }

    // ---- Req 5.2: has_more=false に到達したら nextKey=null（終端） ------------

    @Test
    fun `Req 5-2 has_more false terminates paging with null nextKey`() = runTest {
        // Arrange
        val body = FixtureLoader.load("search_page_terminal.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val repo = newRepository()
        val source = repo.newPagingSource(query = "rust")

        // Act
        val result = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page

        // Assert
        assertNull("Req 5.2: has_more=false → nextKey null", result.nextKey)
    }

    // ---- Req 5.3: 終端到達後の append は no-op ------------------------------

    @Test
    fun `Req 5-3 no further request after terminal reached via TestPager`() = runTest {
        // Arrange: 1 ページ目 has_more=true → 2 ページ目 has_more=false
        val first = FixtureLoader.load("search_page_has_more.json")
        val second = FixtureLoader.load("search_page_second.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(first))
        server.enqueue(MockResponse().setResponseCode(200).setBody(second))
        val repo = newRepository()
        val source = repo.newPagingSource(query = "go")
        val pager = TestPager(
            config = androidx.paging.PagingConfig(pageSize = 50),
            pagingSource = source,
        )

        // Act
        pager.refresh()
        pager.append()
        val third = pager.append()

        // Assert
        assertNull("Req 5.3: 終端到達後の append は no-op（追加リクエスト無し）", third)
        assertEquals("HTTP リクエストは合計 2 回のみ", 2, server.requestCount)
    }

    // ---- Req 5.4: キーワード変更で新しい Pager / 新 PagingSource が生成 ------

    @Test
    fun `Req 5-4 different query produces fresh PagingSource and head fetch`() = runTest {
        // Arrange
        val first = FixtureLoader.load("search_page_has_more.json")
        val secondQueryHead = FixtureLoader.load("search_page_terminal.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(first))
        server.enqueue(MockResponse().setResponseCode(200).setBody(secondQueryHead))
        val repo = newRepository()

        // Act
        val sourceForOld = repo.newPagingSource(query = "kotlin")
        sourceForOld.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        )
        // Req 5.4: キーワード変更 = 新しい PagingSource 生成
        val sourceForNew = repo.newPagingSource(query = "scala")
        val newResult = sourceForNew.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page

        // Assert
        server.takeRequest() // skip old
        val newReq = server.takeRequest()
        assertEquals(
            "Req 5.4: 新しいキーワードでは独立した PagingSource が q を切り替えて先頭から取得",
            "scala",
            newReq.requestUrl?.queryParameter("q"),
        )
        assertNull(
            "Req 5.4: 新しいキーワードの先頭ページは cursor 未指定",
            newReq.requestUrl?.queryParameter("cursor"),
        )
        assertEquals(1, newResult.data.size)
    }

    // ---- Req 6.2: 初回失敗のエラー透過 ---------------------------------------

    @Test
    fun `Req 6-2 initial load failure surfaces FeedmanException via LoadResult Error`() = runTest {
        // Arrange
        val errorBody =
            """{"error":{"code":"UNAUTHORIZED","message":"認証エラー","category":"auth","action":"login"}}"""
        server.enqueue(MockResponse().setResponseCode(401).setBody(errorBody))
        val repo = newRepository()
        val source = repo.newPagingSource(query = "anything")

        // Act
        val result = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        )

        // Assert
        val error = result as PagingSource.LoadResult.Error
        val cause = error.throwable
        assertTrue("Req 6.2: FeedmanException として透過: $cause", cause is FeedmanException)
        assertEquals("UNAUTHORIZED", (cause as FeedmanException).code)
        assertEquals(401, cause.httpStatus)
    }

    @Test
    fun `Req 6-2 network failure surfaces FeedmanException with NETWORK_ERROR code`() = runTest {
        // Arrange: 接続を切断して I/O 失敗を再現
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val repo = newRepository()
        val source = repo.newPagingSource(query = "io")

        // Act
        val result = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        )

        // Assert
        val error = result as? PagingSource.LoadResult.Error
            ?: fail("Expected LoadResult.Error, got $result").let { return@runTest }
        val cause = error.throwable
        assertTrue("Req 6.2: FeedmanException（NETWORK_ERROR）として露出: $cause", cause is FeedmanException)
        assertEquals(FeedmanException.CODE_NETWORK_ERROR, (cause as FeedmanException).code)
    }

    // ---- Req 6.4: 追加ロード失敗は前ページを破棄せずエラーを露出 -------------

    @Test
    fun `Req 6-4 subsequent load failure surfaces error without discarding previous page`() = runTest {
        // Arrange: 1 ページ目成功 → 2 ページ目 5xx
        val first = FixtureLoader.load("search_page_has_more.json")
        val errorBody =
            """{"error":{"code":"INTERNAL","message":"server","category":"server","action":"retry"}}"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(first))
        server.enqueue(MockResponse().setResponseCode(500).setBody(errorBody))
        val repo = newRepository()
        val source = repo.newPagingSource(query = "k8s")

        // Act
        val firstResult = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page
        val failed = source.load(
            PagingSource.LoadParams.Append(
                key = firstResult.nextKey!!,
                loadSize = 50,
                placeholdersEnabled = false,
            ),
        )

        // Assert
        assertEquals("前提: 1 ページ目は 2 件取得済み", 2, firstResult.data.size)
        assertTrue(
            "Req 6.4: 追加ロード失敗は LoadResult.Error として露出（既存 result は破棄されない）",
            failed is PagingSource.LoadResult.Error,
        )
        val cause = (failed as PagingSource.LoadResult.Error).throwable
        assertTrue("FeedmanException として露出", cause is FeedmanException)
        assertEquals("INTERNAL", (cause as FeedmanException).code)
    }

    // ---- NFR 2.2: ItemSearchHit の nullable 伝達 ----------------------------

    @Test
    fun `NFR 2-2 published_at and favicon_url null and non-null are propagated verbatim`() =
        runTest {
            // Arrange
            val body = FixtureLoader.load("search_page_has_more.json")
            server.enqueue(MockResponse().setResponseCode(200).setBody(body))
            val repo = newRepository()
            val source = repo.newPagingSource(query = "any")

            // Act
            val result = source.load(
                PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
            ) as PagingSource.LoadResult.Page

            // Assert
            assertEquals(2, result.data.size)
            // 1 件目: 双方とも非 null
            assertNotNull("NFR 2.2: 1 件目の published_at は非 null", result.data[0].publishedAt)
            assertNotNull("NFR 2.2: 1 件目の favicon_url は非 null", result.data[0].faviconUrl)
            assertEquals("Android Developers", result.data[0].feedTitle)
            assertEquals(8, result.data[0].hatebuCount)
            assertFalse(result.data[0].isStarred)
            // 2 件目: 双方とも null
            assertNull("NFR 2.2: 2 件目の published_at は null", result.data[1].publishedAt)
            assertNull("NFR 2.2: 2 件目の favicon_url は null", result.data[1].faviconUrl)
            assertEquals("Unknown Source", result.data[1].feedTitle)
            assertTrue(result.data[1].isStarred)
        }

    // ---- Repository contract: empty query must not be accepted -------------

    @Test(expected = IllegalArgumentException::class)
    fun `pagingData rejects empty query to guard Req 2-1 and 3-2`() {
        val repo = newRepository()
        // Req 2.1 / 3.2: 空クエリは呼び出し元（ViewModel / 画面）で弾く前提。リポジトリは
        // 仕様逸脱を早期検出するため require で守る。
        repo.pagingData(query = "")
    }
}
