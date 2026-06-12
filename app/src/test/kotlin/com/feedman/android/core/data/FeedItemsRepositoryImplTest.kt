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
 * [FeedItemsRepositoryImpl] の AC 単位検証（Issue #40 requirements.md）。
 *
 * MockWebServer で実 HTTP 経路を再現し、`/api/feeds/{id}/items` のクエリ受け渡し
 * （filter / cursor / limit）と `FeedmanException` 透過、フィルタ変更による先頭ページ再取得、
 * リフレッシュでの先頭ページ再開を検証する。Retrofit / OkHttp / kotlinx.serialization は実物を
 * 使い、`FeedmanApi` をモックしない（CLAUDE.md テスト規約）。
 */
class FeedItemsRepositoryImplTest {

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

    private fun newRepository(): FeedItemsRepositoryImpl {
        val api = ApiClientFactory.create(baseUrl = baseUrl)
        return FeedItemsRepositoryImpl(api)
    }

    // ---- Req 1: フィルタクエリ送出 -------------------------------------------

    @Test
    fun `Req 1-1 initial load with ALL filter forwards filter=all on the request`() = runTest {
        // Arrange
        val body = FixtureLoader.load("item_summary_page_has_more.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val repo = newRepository()
        val source = repo.newPagingSource(feedId = "FEED_A", filter = FeedItemFilter.ALL)

        // Act
        source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        )

        // Assert
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/api/feeds/FEED_A/items", recorded.requestUrl?.encodedPath)
        assertEquals("all", recorded.requestUrl?.queryParameter("filter"))
        assertEquals("50", recorded.requestUrl?.queryParameter("limit"))
        assertNull(
            "初回ロードでは cursor クエリが付かない（Retrofit は null Query を送信しない）",
            recorded.requestUrl?.queryParameter("cursor"),
        )
    }

    @Test
    fun `Req 1-2 initial load with UNREAD filter forwards filter=unread on the request`() = runTest {
        // Arrange
        val body = FixtureLoader.load("item_summary_page_has_more.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val repo = newRepository()
        val source = repo.newPagingSource(feedId = "FEED_A", filter = FeedItemFilter.UNREAD)

        // Act
        source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        )

        // Assert
        val recorded = server.takeRequest()
        assertEquals("/api/feeds/FEED_A/items", recorded.requestUrl?.encodedPath)
        assertEquals("unread", recorded.requestUrl?.queryParameter("filter"))
    }

    @Test
    fun `Req 1-3 initial load with STARRED filter forwards filter=starred on the request`() = runTest {
        // Arrange
        val body = FixtureLoader.load("item_summary_page_has_more.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val repo = newRepository()
        val source = repo.newPagingSource(feedId = "FEED_A", filter = FeedItemFilter.STARRED)

        // Act
        source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        )

        // Assert
        val recorded = server.takeRequest()
        assertEquals("/api/feeds/FEED_A/items", recorded.requestUrl?.encodedPath)
        assertEquals("starred", recorded.requestUrl?.queryParameter("filter"))
    }

    @Test
    fun `Req 1-4 feed id is embedded into request path verbatim without modification`() = runTest {
        // Arrange: 異なる feedId を別のリクエストとして送出する（パス書き換え禁止の確認）
        val body = FixtureLoader.load("item_summary_page_terminal.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val repo = newRepository()

        // Act
        repo.newPagingSource(feedId = "01HGY8K9ZQ4N7TXVY1F8M9R3FE", filter = FeedItemFilter.ALL).load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        )
        repo.newPagingSource(feedId = "feed-with-hyphens", filter = FeedItemFilter.ALL).load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        )

        // Assert
        val first = server.takeRequest()
        val second = server.takeRequest()
        assertEquals("/api/feeds/01HGY8K9ZQ4N7TXVY1F8M9R3FE/items", first.requestUrl?.encodedPath)
        assertEquals("/api/feeds/feed-with-hyphens/items", second.requestUrl?.encodedPath)
    }

    // ---- Req 2: フィルタ変更時のページング再生成 -----------------------------

    @Test
    fun `Req 2-1 changing filter starts from head with new cursor-less request`() = runTest {
        // Arrange: ALL で 1 ページ目を消費 → UNREAD に変更したら初回（cursor なし）になる
        val first = FixtureLoader.load("item_summary_page_has_more.json")
        val second = FixtureLoader.load("item_summary_page_terminal.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(first))
        server.enqueue(MockResponse().setResponseCode(200).setBody(second))
        val repo = newRepository()

        // Act: ALL でロード → UNREAD で新 PagingSource を作って初回ロード
        repo.newPagingSource(feedId = "FEED_A", filter = FeedItemFilter.ALL).load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        )
        repo.newPagingSource(feedId = "FEED_A", filter = FeedItemFilter.UNREAD).load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        )

        // Assert
        val firstReq = server.takeRequest()
        assertEquals("all", firstReq.requestUrl?.queryParameter("filter"))
        assertNull(firstReq.requestUrl?.queryParameter("cursor"))

        val secondReq = server.takeRequest()
        assertEquals(
            "Req 2.1: フィルタ変更後の初回は新 filter かつ cursor なし",
            "unread",
            secondReq.requestUrl?.queryParameter("filter"),
        )
        assertNull(
            "Req 2.1: フィルタ変更後の初回は cursor 未指定（先頭から再取得）",
            secondReq.requestUrl?.queryParameter("cursor"),
        )
    }

    @Test
    fun `Req 2-2 changing filter does not carry previous accumulated pages into new paging state`() = runTest {
        // Arrange: フィルタごとに新しい Pager / PagingSource が返ることを観測する
        val body = FixtureLoader.load("item_summary_page_has_more.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val repo = newRepository()

        // Act
        val flowAll = repo.pagingData(feedId = "FEED_A", filter = FeedItemFilter.ALL)
        val flowUnread = repo.pagingData(feedId = "FEED_A", filter = FeedItemFilter.UNREAD)

        // Assert: 別 Flow（別 Pager）が返る = 新 PagingData ストリームで前フィルタの蓄積を持ち越さない
        assertFalse(
            "Req 2.2: フィルタが異なれば pagingData は別の Pager.flow を返す（蓄積を共有しない）",
            flowAll === flowUnread,
        )
    }

    @Test
    fun `Req 2-3 same filter subsequent load forwards previous next_cursor as cursor query`() = runTest {
        // Arrange: ALL の 1 ページ目 → 2 ページ目（next_cursor を引き継ぐ）
        val first = FixtureLoader.load("item_summary_page_has_more.json")
        val second = FixtureLoader.load("item_summary_page_second.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(first))
        server.enqueue(MockResponse().setResponseCode(200).setBody(second))
        val repo = newRepository()
        val source = repo.newPagingSource(feedId = "FEED_A", filter = FeedItemFilter.ALL)

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
            "Req 2.3: 次ページ要求では前ページの next_cursor がそのままクエリに乗る",
            "2026-06-10T08:00:00Z:01HGY8K9ZQ4N7TXVY1F8M9R3P2",
            appendRequest.requestUrl?.queryParameter("cursor"),
        )
        assertEquals("all", appendRequest.requestUrl?.queryParameter("filter"))
    }

    // ---- Req 3: 終端判定とエラー透過 ----------------------------------------

    @Test
    fun `Req 3-1 has_more false on response terminates paging with null nextKey`() = runTest {
        // Arrange
        val body = FixtureLoader.load("item_summary_page_terminal.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val repo = newRepository()
        val source = repo.newPagingSource(feedId = "FEED_A", filter = FeedItemFilter.ALL)

        // Act
        val result = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page

        // Assert
        assertNull("Req 3.1: has_more=false → nextKey null", result.nextKey)
    }

    @Test
    fun `Req 3-2 null next_cursor terminates paging even if has_more true`() = runTest {
        // Arrange: 「has_more=true だが next_cursor=null」の矛盾 envelope は安全側（= 終端）に倒す
        val body = """
            {
              "items": [{
                "id":"X","feed_id":"F","title":"t","link":"l","summary":"s",
                "published_at":"2026-06-12T09:00:00Z",
                "is_date_estimated":false,"is_read":false,"is_starred":false,
                "hatebu_count":0
              }],
              "next_cursor": null,
              "has_more": true
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val repo = newRepository()
        val source = repo.newPagingSource(feedId = "FEED_A", filter = FeedItemFilter.ALL)

        // Act
        val result = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page

        // Assert
        assertNull("Req 3.2: next_cursor=null は終端", result.nextKey)
    }

    @Test
    fun `Req 3-2 empty string next_cursor terminates paging even if has_more true`() = runTest {
        // Arrange
        val body = """
            {
              "items": [],
              "next_cursor": "",
              "has_more": true
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val repo = newRepository()
        val source = repo.newPagingSource(feedId = "FEED_A", filter = FeedItemFilter.ALL)

        // Act
        val result = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page

        // Assert
        assertNull("Req 3.2: next_cursor=空文字列も終端として扱う", result.nextKey)
    }

    @Test
    fun `Req 3-3 no further request is issued once terminal reached via TestPager`() = runTest {
        // Arrange: 1 ページ目 has_more=true → 2 ページ目 has_more=false
        val first = FixtureLoader.load("item_summary_page_has_more.json")
        val second = FixtureLoader.load("item_summary_page_terminal.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(first))
        server.enqueue(MockResponse().setResponseCode(200).setBody(second))
        val repo = newRepository()
        val source = repo.newPagingSource(feedId = "FEED_A", filter = FeedItemFilter.ALL)
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

    @Test
    fun `Req 3-4 initial load failure surfaces as LoadResult Error with FeedmanException`() = runTest {
        // Arrange
        val errorBody = """
            {"error":{"code":"UNAUTHORIZED","message":"認証エラー","category":"auth","action":"login"}}
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(401).setBody(errorBody))
        val repo = newRepository()
        val source = repo.newPagingSource(feedId = "FEED_A", filter = FeedItemFilter.ALL)

        // Act
        val result = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        )

        // Assert
        val error = result as PagingSource.LoadResult.Error
        val cause = error.throwable
        assertTrue("Req 3.4: FeedmanException として透過: $cause", cause is FeedmanException)
        val fe = cause as FeedmanException
        assertEquals("UNAUTHORIZED", fe.code)
        assertEquals(401, fe.httpStatus)
    }

    @Test
    fun `Req 3-5 subsequent load failure surfaces error without discarding previous page`() = runTest {
        // Arrange: 1 ページ目成功 → 2 ページ目 5xx
        val first = FixtureLoader.load("item_summary_page_has_more.json")
        val errorBody =
            """{"error":{"code":"INTERNAL","message":"server","category":"server","action":"retry"}}"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(first))
        server.enqueue(MockResponse().setResponseCode(500).setBody(errorBody))
        val repo = newRepository()
        val source = repo.newPagingSource(feedId = "FEED_A", filter = FeedItemFilter.ALL)

        // Act
        val firstResult = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page
        val failed = source.load(
            PagingSource.LoadParams.Append(key = firstResult.nextKey!!, loadSize = 50, placeholdersEnabled = false),
        )

        // Assert
        assertEquals("前提: 1 ページ目は 2 件取得済み", 2, firstResult.data.size)
        assertTrue("Req 3.5: 追加ロード失敗は LoadResult.Error として露出", failed is PagingSource.LoadResult.Error)
        val cause = (failed as PagingSource.LoadResult.Error).throwable
        assertTrue("FeedmanException として露出", cause is FeedmanException)
        assertEquals("INTERNAL", (cause as FeedmanException).code)
    }

    @Test
    fun `Req 3-6 retry after failure reissues request with same feedId filter and cursor`() = runTest {
        // Arrange: 1 ページ目成功 → 2 ページ目 5xx → retry で 2 ページ目を成功させる
        val first = FixtureLoader.load("item_summary_page_has_more.json")
        val errorBody =
            """{"error":{"code":"INTERNAL","message":"server","category":"server","action":"retry"}}"""
        val recovered = FixtureLoader.load("item_summary_page_terminal.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(first))
        server.enqueue(MockResponse().setResponseCode(500).setBody(errorBody))
        server.enqueue(MockResponse().setResponseCode(200).setBody(recovered))
        val repo = newRepository()
        val source = repo.newPagingSource(feedId = "FEED_X", filter = FeedItemFilter.UNREAD)

        // Act
        val firstResult = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page
        val nextKey = firstResult.nextKey!!
        // 失敗
        source.load(
            PagingSource.LoadParams.Append(key = nextKey, loadSize = 50, placeholdersEnabled = false),
        )
        // 再試行（Paging 3 の retry() は同一 key で load を再呼び出しする規約）
        val retried = source.load(
            PagingSource.LoadParams.Append(key = nextKey, loadSize = 50, placeholdersEnabled = false),
        )

        // Assert
        assertTrue("再試行は LoadResult.Page で成功", retried is PagingSource.LoadResult.Page)
        server.takeRequest() // skip initial
        val failedReq = server.takeRequest()
        val retriedReq = server.takeRequest()
        // 失敗時と再試行時で feedId / filter / cursor が同一であることを確認（Req 3.6）
        assertEquals(failedReq.requestUrl?.encodedPath, retriedReq.requestUrl?.encodedPath)
        assertEquals(
            failedReq.requestUrl?.queryParameter("filter"),
            retriedReq.requestUrl?.queryParameter("filter"),
        )
        assertEquals(
            failedReq.requestUrl?.queryParameter("cursor"),
            retriedReq.requestUrl?.queryParameter("cursor"),
        )
        assertEquals("unread", retriedReq.requestUrl?.queryParameter("filter"))
        assertEquals("/api/feeds/FEED_X/items", retriedReq.requestUrl?.encodedPath)
    }

    @Test
    fun `Req 3-error network failure surfaces FeedmanException with NETWORK_ERROR code`() = runTest {
        // Arrange: 接続を切断して I/O 失敗を再現
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val repo = newRepository()
        val source = repo.newPagingSource(feedId = "FEED_A", filter = FeedItemFilter.ALL)

        // Act
        val result = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        )

        // Assert
        val error = result as? PagingSource.LoadResult.Error
            ?: fail("Expected LoadResult.Error, got $result").let { return@runTest }
        val cause = error.throwable
        assertTrue("Req 3.4: FeedmanException（NETWORK_ERROR）として露出: $cause", cause is FeedmanException)
        assertEquals(FeedmanException.CODE_NETWORK_ERROR, (cause as FeedmanException).code)
    }

    // ---- Req 4: リフレッシュ挙動 --------------------------------------------

    @Test
    fun `Req 4-1 refresh on same filter restarts from head with cursor unset and same filter`() = runTest {
        // Arrange: 1 セッション目 → リフレッシュ（新 PagingSource）で先頭ページから再取得
        val first = FixtureLoader.load("item_summary_page_has_more.json")
        val refreshed = FixtureLoader.load("item_summary_page_terminal.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(first))
        server.enqueue(MockResponse().setResponseCode(200).setBody(refreshed))
        val repo = newRepository()

        // Act
        val source1 = repo.newPagingSource(feedId = "FEED_A", filter = FeedItemFilter.STARRED)
        source1.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        )
        // リフレッシュ = 同 filter のまま新しい PagingSource を生成
        val source2 = repo.newPagingSource(feedId = "FEED_A", filter = FeedItemFilter.STARRED)
        source2.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        )

        // Assert
        server.takeRequest() // skip initial of session 1
        val refreshedReq = server.takeRequest()
        assertEquals("/api/feeds/FEED_A/items", refreshedReq.requestUrl?.encodedPath)
        assertEquals(
            "Req 4.1: リフレッシュ後の先頭ページも同一 filter を維持",
            "starred",
            refreshedReq.requestUrl?.queryParameter("filter"),
        )
        assertNull(
            "Req 4.1: リフレッシュ後の先頭ページは cursor 未指定",
            refreshedReq.requestUrl?.queryParameter("cursor"),
        )
    }

    @Test
    fun `Req 4-2 after refresh next_cursor and has_more are handled by the same paging rules`() = runTest {
        // Arrange: refresh 後の先頭ページが has_more=false → nextKey null（Req 3 と同じ規則）
        val first = FixtureLoader.load("item_summary_page_has_more.json")
        val terminal = FixtureLoader.load("item_summary_page_terminal.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(first))
        server.enqueue(MockResponse().setResponseCode(200).setBody(terminal))
        val repo = newRepository()

        // Act
        repo.newPagingSource(feedId = "FEED_A", filter = FeedItemFilter.ALL).load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        )
        val refreshedResult = repo.newPagingSource(feedId = "FEED_A", filter = FeedItemFilter.ALL).load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page

        // Assert
        assertNotNull(refreshedResult.data)
        assertNull(
            "Req 4.2: リフレッシュ後の先頭ページの終端判定は Req 3 と同じ規則（has_more=false → nextKey null）",
            refreshedResult.nextKey,
        )
    }
}
