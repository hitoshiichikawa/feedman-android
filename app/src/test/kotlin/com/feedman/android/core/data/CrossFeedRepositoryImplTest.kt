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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * [CrossFeedRepositoryImpl] の AC 単位検証（Issue #32 requirements.md）。
 *
 * MockWebServer で実 HTTP 経路を再現し、`/api/items/cross-feed` のクエリ受け渡し
 * （cursor / since_time / limit）と `FeedmanException` 透過、リフレッシュでの since_time
 * リセットを検証する。Retrofit / OkHttp / kotlinx.serialization は実物を使い、
 * `FeedmanApi` をモックしない（CLAUDE.md テスト規約）。
 */
class CrossFeedRepositoryImplTest {

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

    private fun newRepository(): CrossFeedRepositoryImpl {
        val api = ApiClientFactory.create(baseUrl = baseUrl)
        return CrossFeedRepositoryImpl(api)
    }

    // ---- Req 1: 初回ロード ----------------------------------------------------

    @Test
    fun `Req 1-1 initial load issues GET cross-feed with limit 50 and no cursor`() = runTest {
        // Arrange
        val body = FixtureLoader.load("cross_feed_page.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val repo = newRepository()
        val source = repo.newPagingSource()

        // Act
        val result = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        )

        // Assert
        val page = result as PagingSource.LoadResult.Page
        assertEquals(2, page.data.size)
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/api/items/cross-feed", recorded.requestUrl?.encodedPath)
        assertEquals("50", recorded.requestUrl?.queryParameter("limit"))
        assertNull(
            "初回ロードでは cursor クエリが付かない（Retrofit は null Query を送信しない）",
            recorded.requestUrl?.queryParameter("cursor"),
        )
        assertNull(
            "初回ロードでは since_time クエリが付かない（Req 1.1 / セッション未保持）",
            recorded.requestUrl?.queryParameter("since_time"),
        )
    }

    @Test
    fun `Req 1-2 initial response since_time is captured into session state`() = runTest {
        // Arrange
        val body = FixtureLoader.load("cross_feed_page.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val repo = newRepository()
        val source = repo.newPagingSource()
        assertNull("初回ロード前は currentSinceTime が null（NFR 1.2）", repo.currentSinceTime)

        // Act
        source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        )

        // Assert
        assertEquals(
            "Req 1.2: 初回レスポンスの since_time をセッション保持値として固定する",
            "2026-06-12T09:30:00Z",
            repo.currentSinceTime,
        )
    }

    @Test
    fun `Req 1-3 initial page items are returned to Paging as LoadResult Page data`() = runTest {
        // Arrange
        val body = FixtureLoader.load("cross_feed_page.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val repo = newRepository()
        val source = repo.newPagingSource()

        // Act
        val result = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        )

        // Assert
        val page = result as PagingSource.LoadResult.Page
        assertEquals("01HGY8K9ZQ4N7TXVY1F8M9R3X1", page.data[0].id)
        assertEquals("Feedman Dev Blog", page.data[0].feedTitle)
        assertEquals("2026-06-12T08:30:00Z:01HGY8K9ZQ4N7TXVY1F8M9R3X2", page.nextKey)
    }

    @Test
    fun `Req 1-4 missing since_time on initial response is reported as FeedmanException`() = runTest {
        // Arrange: since_time を空文字列で返す（kotlinx.serialization 上は decode 可能）
        val body = """
            {
              "items": [],
              "next_cursor": null,
              "has_more": false,
              "since_time": ""
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val repo = newRepository()
        val source = repo.newPagingSource()

        // Act
        val result = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        )

        // Assert
        val error = result as PagingSource.LoadResult.Error
        val cause = error.throwable
        assertTrue("Req 1.4: FeedmanException として露出する: $cause", cause is FeedmanException)
        assertEquals(FeedmanException.CODE_UNKNOWN_ERROR, (cause as FeedmanException).code)
        assertNull(
            "Req 1.4: 初回読み込みは中断され since_time は保持されない",
            repo.currentSinceTime,
        )
    }

    // ---- Req 2: 後続ページの since_time / cursor 引き継ぎ ----------------------

    @Test
    fun `Req 2-1 subsequent load forwards cursor and session since_time as query params`() = runTest {
        // Arrange
        val first = FixtureLoader.load("cross_feed_page.json")
        val second = FixtureLoader.load("cross_feed_page_second.json")
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
        val secondRequest = server.takeRequest()
        assertEquals(
            "Req 2.1: cursor=<前回 next_cursor>",
            "2026-06-12T08:30:00Z:01HGY8K9ZQ4N7TXVY1F8M9R3X2",
            secondRequest.requestUrl?.queryParameter("cursor"),
        )
        assertEquals(
            "Req 2.1: since_time=<セッション保持値>",
            "2026-06-12T09:30:00Z",
            secondRequest.requestUrl?.queryParameter("since_time"),
        )
        assertEquals(
            "Req 2.4: 後続ロードも limit=50",
            "50",
            secondRequest.requestUrl?.queryParameter("limit"),
        )
    }

    @Test
    fun `Req 2-2 session since_time is not overwritten by subsequent response since_time`() = runTest {
        // Arrange: 2 ページ目は意図的に異なる since_time（2099-12-31）を返すが、セッション保持値は
        // 初回値を維持する（Req 2.2）。
        val first = FixtureLoader.load("cross_feed_page.json") // since_time=2026-06-12T09:30:00Z
        val second = FixtureLoader.load("cross_feed_page_second.json") // since_time=2099-12-31T00:00:00Z
        server.enqueue(MockResponse().setResponseCode(200).setBody(first))
        server.enqueue(MockResponse().setResponseCode(200).setBody(second))
        val repo = newRepository()
        val source = repo.newPagingSource()

        // Act
        val firstResult = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page
        source.load(
            PagingSource.LoadParams.Append(key = firstResult.nextKey!!, loadSize = 50, placeholdersEnabled = false),
        )

        // Assert
        assertEquals(
            "Req 2.2: セッション保持の since_time は初回値のまま",
            "2026-06-12T09:30:00Z",
            repo.currentSinceTime,
        )
    }

    @Test
    fun `Req 2-3 next_cursor is updated to subsequent response value`() = runTest {
        // Arrange
        val first = FixtureLoader.load("cross_feed_page.json")
        val second = FixtureLoader.load("cross_feed_page_second.json") // next_cursor: null
        server.enqueue(MockResponse().setResponseCode(200).setBody(first))
        server.enqueue(MockResponse().setResponseCode(200).setBody(second))
        val repo = newRepository()
        val source = repo.newPagingSource()

        // Act
        val firstResult = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page
        val appendResult = source.load(
            PagingSource.LoadParams.Append(key = firstResult.nextKey!!, loadSize = 50, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page

        // Assert: 1 ページ目の next_cursor と 2 ページ目の次キーが異なる（次キーが更新されている）
        assertEquals("2026-06-12T08:30:00Z:01HGY8K9ZQ4N7TXVY1F8M9R3X2", firstResult.nextKey)
        assertNull("Req 2.3 / 3.2: 2 ページ目の next_cursor は null → 終端", appendResult.nextKey)
    }

    // ---- Req 3: 終端判定 -----------------------------------------------------

    @Test
    fun `Req 3-1 has_more false on initial response terminates paging`() = runTest {
        // Arrange: has_more=false の終端ページのみ
        val body = FixtureLoader.load("cross_feed_page_terminal.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val repo = newRepository()
        val source = repo.newPagingSource()

        // Act
        val result = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page

        // Assert
        assertNull("Req 3.1: has_more=false → nextKey null", result.nextKey)
    }

    @Test
    fun `Req 3-3 no further request is issued once terminal reached via TestPager`() = runTest {
        // Arrange: 1 ページ目 has_more=true → 2 ページ目 has_more=false
        val first = FixtureLoader.load("cross_feed_page.json")
        val second = FixtureLoader.load("cross_feed_page_second.json")
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
        assertNull("Req 3.3: 終端到達後の append は no-op（追加リクエスト無し）", third)
        assertEquals("HTTP リクエストは合計 2 回のみ", 2, server.requestCount)
    }

    // ---- Req 4: リフレッシュによる since_time リセット -----------------------

    @Test
    fun `Req 4-1 refresh discards session since_time and cursor`() = runTest {
        // Arrange
        val body = FixtureLoader.load("cross_feed_page.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val repo = newRepository()
        val source = repo.newPagingSource()
        source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        )
        assertNotNull("前提: 初回読み込み後 currentSinceTime に値あり", repo.currentSinceTime)

        // Act: 新セッション開始（= Pager 内部の invalidate 相当）
        repo.newPagingSource()

        // Assert
        assertNull("Req 4.1: 新セッション開始時に保持値は破棄される", repo.currentSinceTime)
    }

    @Test
    fun `Req 4-2 after refresh new initial response since_time becomes session held value`() = runTest {
        // Arrange
        val first = FixtureLoader.load("cross_feed_page.json") // since_time=2026-06-12T09:30:00Z
        val refreshed = FixtureLoader.load("cross_feed_page_second.json") // since_time=2099-12-31T00:00:00Z
        server.enqueue(MockResponse().setResponseCode(200).setBody(first))
        server.enqueue(MockResponse().setResponseCode(200).setBody(refreshed))
        val repo = newRepository()

        // Act: セッション 1 で初回 load → 新セッションで再 load
        val source1 = repo.newPagingSource()
        source1.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        )
        val firstSinceTime = repo.currentSinceTime
        val source2 = repo.newPagingSource()
        source2.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        )

        // Assert
        assertEquals("2026-06-12T09:30:00Z", firstSinceTime)
        assertEquals(
            "Req 4.2: リフレッシュ後の新初回レスポンスの since_time が新セッション保持値",
            "2099-12-31T00:00:00Z",
            repo.currentSinceTime,
        )
        assertNotEquals(firstSinceTime, repo.currentSinceTime)
    }

    @Test
    fun `Req 4-3 after refresh subsequent request uses the new since_time`() = runTest {
        // Arrange: refresh 後の初回 → 次に append（since_time は新初回値）
        // セッション 1 を捨てて、セッション 2 で初回 + 後続を投げる。
        val refreshed = """
            {
              "items": [{
                "id":"X","feed_id":"F","feed_title":"T","feed_favicon_url":null,
                "title":"t","link":"l","summary":"s","published_at":"2026-06-12T09:00:00Z",
                "is_date_estimated":false,"is_read":false,"is_starred":false,"hatebu_count":0
              }],
              "next_cursor":"NEXT_CURSOR_X",
              "has_more":true,
              "since_time":"2099-12-31T00:00:00Z"
            }
        """.trimIndent()
        val refreshedSecond = FixtureLoader.load("cross_feed_page_terminal.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(refreshed))
        server.enqueue(MockResponse().setResponseCode(200).setBody(refreshedSecond))
        val repo = newRepository()
        val source = repo.newPagingSource()

        // Act
        val initial = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page
        source.load(
            PagingSource.LoadParams.Append(key = initial.nextKey!!, loadSize = 50, placeholdersEnabled = false),
        )

        // Assert
        server.takeRequest() // skip initial
        val appendReq = server.takeRequest()
        assertEquals(
            "Req 4.3: refresh 後の after-page は新 since_time を送信",
            "2099-12-31T00:00:00Z",
            appendReq.requestUrl?.queryParameter("since_time"),
        )
        assertEquals(
            "NEXT_CURSOR_X",
            appendReq.requestUrl?.queryParameter("cursor"),
        )
    }

    // ---- Req 5: エラー応答時の挙動 -------------------------------------------

    @Test
    fun `Req 5-1 non-2xx response surfaces FeedmanException carrying code and message`() = runTest {
        // Arrange: SPEC §4.3 のエラーボディ
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
        assertTrue("FeedmanException として透過: $cause", cause is FeedmanException)
        val fe = cause as FeedmanException
        assertEquals("UNAUTHORIZED", fe.code)
        assertEquals("認証エラー", fe.errorMessage)
        assertEquals(401, fe.httpStatus)
    }

    @Test
    fun `Req 5-2 network failure surfaces FeedmanException with NETWORK_ERROR code`() = runTest {
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
        assertTrue("Req 5.2: FeedmanException（NETWORK_ERROR）として露出: $cause", cause is FeedmanException)
        assertEquals(FeedmanException.CODE_NETWORK_ERROR, (cause as FeedmanException).code)
    }

    @Test
    fun `Req 5-3 subsequent failure preserves session since_time`() = runTest {
        // Arrange: 1 ページ目成功 → 2 ページ目 5xx
        val first = FixtureLoader.load("cross_feed_page.json")
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
        val sinceTimeBefore = repo.currentSinceTime
        val failed = source.load(
            PagingSource.LoadParams.Append(key = firstResult.nextKey!!, loadSize = 50, placeholdersEnabled = false),
        )

        // Assert
        assertTrue(failed is PagingSource.LoadResult.Error)
        assertEquals(
            "Req 5.3: 後続ページ失敗時もセッションの since_time は保持",
            sinceTimeBefore,
            repo.currentSinceTime,
        )
        assertEquals("2026-06-12T09:30:00Z", repo.currentSinceTime)
    }

    // ---- NFR 1.2: テストから since_time を観測可能 ---------------------------

    @Test
    fun `NFR 1-2 currentSinceTime is observable from tests without time stubbing`() = runTest {
        // Arrange
        val body = FixtureLoader.load("cross_feed_page.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val repo = newRepository()
        val source = repo.newPagingSource()
        // Initial state observability
        assertNull(repo.currentSinceTime)

        // Act
        source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        )

        // Assert
        assertEquals("2026-06-12T09:30:00Z", repo.currentSinceTime)
    }
}
