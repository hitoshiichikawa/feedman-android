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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * [StarredItemsRepositoryImpl] の AC 単位検証（Issue #46 requirements.md）。
 *
 * MockWebServer で実 HTTP 経路を再現し、`/api/feeds/starred/items` のカーソル受け渡し、
 * `FeedmanException` 透過、リフレッシュでの先頭ページ再開、レスポンス内 `feed_title` の
 * 上位伝達を検証する。Retrofit / OkHttp / kotlinx.serialization は実物を使い、
 * `FeedmanApi` をモックしない（CLAUDE.md テスト規約）。
 */
class StarredItemsRepositoryImplTest {

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

    private fun newRepository(): StarredItemsRepositoryImpl {
        val api = ApiClientFactory.create(baseUrl = baseUrl)
        return StarredItemsRepositoryImpl(api)
    }

    // ---- Req 2.1: 初回ロードは cursor 未指定で先頭ページ -------------------

    @Test
    fun `Req 2-1 initial load issues GET with no cursor query`() = runTest {
        // Arrange
        val body = FixtureLoader.load("starred_page_has_more.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val repo = newRepository()
        val source = repo.newPagingSource()

        // Act
        source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        )

        // Assert
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/api/feeds/starred/items", recorded.requestUrl?.encodedPath)
        assertEquals("50", recorded.requestUrl?.queryParameter("limit"))
        assertNull(
            "Req 2.1: 初回ロードでは cursor クエリが付かない（Retrofit は null Query を送信しない）",
            recorded.requestUrl?.queryParameter("cursor"),
        )
    }

    // ---- Req 2.2: 後続ロードは前ページの next_cursor を搬送 -----------------

    @Test
    fun `Req 2-2 subsequent load forwards previous next_cursor as cursor query`() = runTest {
        // Arrange
        val first = FixtureLoader.load("starred_page_has_more.json")
        val second = FixtureLoader.load("starred_page_second.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(first))
        server.enqueue(MockResponse().setResponseCode(200).setBody(second))
        val repo = newRepository()
        val source = repo.newPagingSource()

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
            "Req 2.2: 次ページ要求では前ページの next_cursor がそのままクエリに乗る",
            "2026-06-10T08:00:00Z:01HSTAR000000000000000001B",
            appendRequest.requestUrl?.queryParameter("cursor"),
        )
        assertEquals("/api/feeds/starred/items", appendRequest.requestUrl?.encodedPath)
    }

    // ---- Req 2.3 / 2.4: 終端判定 ---------------------------------------------

    @Test
    fun `Req 2-3 has_more false on response terminates paging with null nextKey`() = runTest {
        // Arrange
        val body = FixtureLoader.load("starred_page_terminal.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val repo = newRepository()
        val source = repo.newPagingSource()

        // Act
        val result = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page

        // Assert
        assertNull("Req 2.3: has_more=false → nextKey null", result.nextKey)
    }

    @Test
    fun `Req 2-4 no further request is issued after terminal reached via TestPager`() = runTest {
        // Arrange: 1 ページ目 has_more=true → 2 ページ目 has_more=false
        val first = FixtureLoader.load("starred_page_has_more.json")
        val second = FixtureLoader.load("starred_page_terminal.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(first))
        server.enqueue(MockResponse().setResponseCode(200).setBody(second))
        val repo = newRepository()
        val source = repo.newPagingSource()
        val pager = TestPager(
            config = androidx.paging.PagingConfig(pageSize = 50),
            pagingSource = source,
        )

        // Act
        pager.refresh()
        pager.append()
        val third = pager.append()

        // Assert
        assertNull("Req 2.4: 終端到達後の append は no-op（追加リクエスト無し）", third)
        assertEquals("HTTP リクエストは合計 2 回のみ", 2, server.requestCount)
    }

    // ---- Req 2.5: 初回失敗のエラー透過 --------------------------------------

    @Test
    fun `Req 2-5 initial load failure surfaces as LoadResult Error with FeedmanException`() = runTest {
        // Arrange
        val errorBody = """
            {"error":{"code":"UNAUTHORIZED","message":"認証エラー","category":"auth","action":"login"}}
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(401).setBody(errorBody))
        val repo = newRepository()
        val source = repo.newPagingSource()

        // Act
        val result = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        )

        // Assert
        val error = result as PagingSource.LoadResult.Error
        val cause = error.throwable
        assertTrue("Req 2.5: FeedmanException として透過: $cause", cause is FeedmanException)
        val fe = cause as FeedmanException
        assertEquals("UNAUTHORIZED", fe.code)
        assertEquals(401, fe.httpStatus)
    }

    @Test
    fun `Req 2-5 network failure surfaces FeedmanException with NETWORK_ERROR code`() = runTest {
        // Arrange: 接続を切断して I/O 失敗を再現
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val repo = newRepository()
        val source = repo.newPagingSource()

        // Act
        val result = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        )

        // Assert
        val error = result as? PagingSource.LoadResult.Error
            ?: fail("Expected LoadResult.Error, got $result").let { return@runTest }
        val cause = error.throwable
        assertTrue("Req 2.5: FeedmanException（NETWORK_ERROR）として露出: $cause", cause is FeedmanException)
        assertEquals(FeedmanException.CODE_NETWORK_ERROR, (cause as FeedmanException).code)
    }

    // ---- Req 2.6: 追加ロード失敗は前ページを破棄せずエラーを露出 -----------

    @Test
    fun `Req 2-6 subsequent load failure surfaces error without discarding previous page`() = runTest {
        // Arrange: 1 ページ目成功 → 2 ページ目 5xx
        val first = FixtureLoader.load("starred_page_has_more.json")
        val errorBody =
            """{"error":{"code":"INTERNAL","message":"server","category":"server","action":"retry"}}"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(first))
        server.enqueue(MockResponse().setResponseCode(500).setBody(errorBody))
        val repo = newRepository()
        val source = repo.newPagingSource()

        // Act
        val firstResult = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page
        val failed = source.load(
            PagingSource.LoadParams.Append(key = firstResult.nextKey!!, loadSize = 50, placeholdersEnabled = false),
        )

        // Assert
        assertEquals("前提: 1 ページ目は 2 件取得済み", 2, firstResult.data.size)
        assertTrue("Req 2.6: 追加ロード失敗は LoadResult.Error として露出", failed is PagingSource.LoadResult.Error)
        val cause = (failed as PagingSource.LoadResult.Error).throwable
        assertTrue("FeedmanException として露出", cause is FeedmanException)
        assertEquals("INTERNAL", (cause as FeedmanException).code)
    }

    // ---- Req 3.2 / 3.3: リフレッシュ規約 -------------------------------------

    @Test
    fun `Req 3-2 refresh issues new request from head with cursor unset`() = runTest {
        // Arrange: 1 セッション目 → リフレッシュ（新 PagingSource）で先頭ページから再取得
        val first = FixtureLoader.load("starred_page_has_more.json")
        val refreshed = FixtureLoader.load("starred_page_terminal.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(first))
        server.enqueue(MockResponse().setResponseCode(200).setBody(refreshed))
        val repo = newRepository()

        // Act
        val source1 = repo.newPagingSource()
        source1.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        )
        // リフレッシュ = 新しい PagingSource を生成
        val source2 = repo.newPagingSource()
        val refreshedResult = source2.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page

        // Assert
        server.takeRequest() // skip initial of session 1
        val refreshedReq = server.takeRequest()
        assertEquals("/api/feeds/starred/items", refreshedReq.requestUrl?.encodedPath)
        assertNull(
            "Req 3.2: リフレッシュ後の先頭ページは cursor 未指定",
            refreshedReq.requestUrl?.queryParameter("cursor"),
        )
        // Req 3.3: 終端判定も同じ規則
        assertNull(
            "Req 3.3: リフレッシュ後の先頭ページの終端判定は通常ロードと同じ規則",
            refreshedResult.nextKey,
        )
    }

    @Test
    fun `Req 3-4 refresh failure surfaces as LoadResult Error`() = runTest {
        // Arrange
        val errorBody = """
            {"error":{"code":"INTERNAL","message":"server","category":"server","action":"retry"}}
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(500).setBody(errorBody))
        val repo = newRepository()
        val source = repo.newPagingSource()

        // Act
        val result = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        )

        // Assert
        assertTrue("Req 3.4: リフレッシュ失敗は LoadResult.Error として露出", result is PagingSource.LoadResult.Error)
    }

    // ---- NFR 2.3: feed_title が呼び出し元へ伝達される ------------------------

    @Test
    fun `NFR 2-3 response feed_title is propagated to caller verbatim`() = runTest {
        // Arrange
        val body = FixtureLoader.load("starred_page_has_more.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val repo = newRepository()
        val source = repo.newPagingSource()

        // Act
        val result = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page

        // Assert
        assertEquals(2, result.data.size)
        assertEquals(
            "NFR 2.3: 1 件目の feed_title が API レスポンスからそのまま伝達される",
            "Android Developers",
            result.data[0].feedTitle,
        )
        assertEquals(
            "NFR 2.3: 2 件目の feed_title が API レスポンスからそのまま伝達される",
            "Kotlin Blog",
            result.data[1].feedTitle,
        )
    }
}
