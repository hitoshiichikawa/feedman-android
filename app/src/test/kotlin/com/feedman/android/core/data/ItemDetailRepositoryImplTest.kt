package com.feedman.android.core.data

import com.feedman.android.core.model.FixtureLoader
import com.feedman.android.core.network.ApiClientFactory
import com.feedman.android.core.network.FeedmanException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * [ItemDetailRepositoryImpl] の AC 単位検証（Issue #35 requirements.md）。
 *
 * MockWebServer で実 HTTP 経路を再現し、`GET /api/items/{id}` と
 * `PUT /api/items/{id}/state` の契約（パス・メソッド・body 構造・エラー透過）を
 * 検証する。Retrofit / OkHttp / kotlinx.serialization は実物を使い、
 * `FeedmanApi` をモックしない（CLAUDE.md テスト規約）。
 */
class ItemDetailRepositoryImplTest {

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

    private fun newRepository(): ItemDetailRepositoryImpl {
        val api = ApiClientFactory.create(baseUrl = baseUrl)
        return ItemDetailRepositoryImpl(api)
    }

    /** PUT リクエスト body を JSON オブジェクトとして解析する補助関数。 */
    private fun parseBody(body: String): JsonObject =
        Json.parseToJsonElement(body).jsonObject

    /**
     * suspend ブロックを実行し、[FeedmanException] が throw されたらそれを返す補助関数。
     * 例外が出ない / 別型の例外が出た場合は [fail] でテストを失敗させる。
     */
    private suspend fun captureFeedmanException(block: suspend () -> Unit): FeedmanException {
        try {
            block()
        } catch (e: FeedmanException) {
            return e
        }
        fail("Expected FeedmanException, but block returned normally")
        error("unreachable")
    }

    // ===== Requirement 1: 記事詳細取得 =======================================

    @Test
    fun `Req 1-1 getItem issues GET to api items with item id path`() = runTest {
        // Arrange
        val body = FixtureLoader.load("item_detail.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val repo = newRepository()

        // Act
        repo.getItem("01HGY8K9ZQ4N7TXVY1F8M9R3D3")

        // Assert
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals(
            "/api/items/01HGY8K9ZQ4N7TXVY1F8M9R3D3",
            recorded.requestUrl?.encodedPath,
        )
    }

    @Test
    fun `Req 1-2 getItem decodes 200 response into ItemDetail with content and author`() = runTest {
        // Arrange
        val body = FixtureLoader.load("item_detail.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val repo = newRepository()

        // Act
        val detail = repo.getItem("01HGY8K9ZQ4N7TXVY1F8M9R3D3")

        // Assert: ItemSummary 相当のフィールド + content + author が揃う（Req 1.2）
        assertEquals("01HGY8K9ZQ4N7TXVY1F8M9R3D3", detail.id)
        assertEquals("01HGY8K9ZQ4N7TXVY1F8M9R3FE", detail.feedId)
        assertEquals("詳細記事のサンプル", detail.title)
        assertEquals("https://example.com/blog/detail", detail.link)
        assertEquals("本文プレビュー用の概要", detail.summary)
        assertEquals("2026-06-07T10:00:00Z", detail.publishedAt)
        assertEquals(false, detail.isDateEstimated)
        assertEquals(true, detail.isRead)
        assertEquals(false, detail.isStarred)
        assertEquals(7, detail.hatebuCount)
        assertEquals("2026-06-07T11:00:00Z", detail.hatebuFetchedAt)
        assertEquals("<p>これは sanitized HTML 本文のサンプルです。</p>", detail.content)
        assertEquals("Feedman Test", detail.author)
    }

    @Test
    fun `Req 1-3 getItem retains is_date_estimated true flag for downstream estimated label`() = runTest {
        // Arrange: is_date_estimated を true に上書きしたボディを返す
        val body = """
            {
              "id":"X","feed_id":"F","title":"t","link":"l","summary":"s",
              "published_at":"2026-06-07T10:00:00Z",
              "is_date_estimated":true,
              "is_read":false,"is_starred":false,"hatebu_count":0,
              "hatebu_fetched_at":null,
              "content":"<p>body</p>","author":"a"
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val repo = newRepository()

        // Act
        val detail = repo.getItem("X")

        // Assert: Req 1.3 — is_date_estimated=true がそのまま伝搬される
        assertTrue("Req 1.3: is_date_estimated は true として保持", detail.isDateEstimated)
    }

    @Test
    fun `Req 1-4 getItem retains nullable hatebu_fetched_at as null without dropping field`() = runTest {
        // Arrange: hatebu_fetched_at を明示的に null として返す
        val body = """
            {
              "id":"X","feed_id":"F","title":"t","link":"l","summary":"s",
              "published_at":"2026-06-07T10:00:00Z",
              "is_date_estimated":false,
              "is_read":false,"is_starred":false,"hatebu_count":0,
              "hatebu_fetched_at":null,
              "content":"<p>body</p>","author":"a"
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val repo = newRepository()

        // Act
        val detail = repo.getItem("X")

        // Assert: Req 1.4 — null は欠落として扱わず ItemDetail.hatebuFetchedAt = null
        assertNull(
            "Req 1.4: nullable フィールドは null として保持される",
            detail.hatebuFetchedAt,
        )
    }

    // ===== Requirement 2: 既読 / スター状態更新 ===============================

    @Test
    fun `Req 2-1 updateState issues PUT to api items state with item id path`() = runTest {
        // Arrange: 成功応答（ボディは記事更新後の状態。リポジトリ呼び出し元は無視する）
        val responseBody = FixtureLoader.load("cross_feed_item.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(responseBody))
        val repo = newRepository()

        // Act
        repo.updateState(
            itemId = "01HGY8K9ZQ4N7TXVY1F8M9R3X1",
            isRead = true,
            isStarred = null,
        )

        // Assert
        val recorded = server.takeRequest()
        assertEquals("PUT", recorded.method)
        assertEquals(
            "/api/items/01HGY8K9ZQ4N7TXVY1F8M9R3X1/state",
            recorded.requestUrl?.encodedPath,
        )
    }

    @Test
    fun `Req 2-2 updateState with only isRead sends body containing only is_read`() = runTest {
        // Arrange
        val responseBody = FixtureLoader.load("cross_feed_item.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(responseBody))
        val repo = newRepository()

        // Act
        repo.updateState(itemId = "X", isRead = true, isStarred = null)

        // Assert: body JSON のキーセットが is_read だけで is_starred を含まない（Req 2.2）
        val recorded = server.takeRequest()
        val obj = parseBody(recorded.body.readUtf8())
        assertEquals(true, obj["is_read"]?.jsonPrimitive?.boolean)
        assertFalse(
            "Req 2.2: isStarred=null のときは body に is_starred が乗らない",
            obj.containsKey("is_starred"),
        )
    }

    @Test
    fun `Req 2-3 updateState with only isStarred sends body containing only is_starred`() = runTest {
        // Arrange
        val responseBody = FixtureLoader.load("cross_feed_item.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(responseBody))
        val repo = newRepository()

        // Act
        repo.updateState(itemId = "X", isRead = null, isStarred = true)

        // Assert: body JSON のキーセットが is_starred だけで is_read を含まない（Req 2.3）
        val recorded = server.takeRequest()
        val obj = parseBody(recorded.body.readUtf8())
        assertEquals(true, obj["is_starred"]?.jsonPrimitive?.boolean)
        assertFalse(
            "Req 2.3: isRead=null のときは body に is_read が乗らない",
            obj.containsKey("is_read"),
        )
    }

    @Test
    fun `Req 2-4 updateState with both flags sends both is_read and is_starred`() = runTest {
        // Arrange
        val responseBody = FixtureLoader.load("cross_feed_item.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(responseBody))
        val repo = newRepository()

        // Act
        repo.updateState(itemId = "X", isRead = false, isStarred = true)

        // Assert: 両キーが乗る（Req 2.4）。値の正しさも確認する
        val recorded = server.takeRequest()
        val obj = parseBody(recorded.body.readUtf8())
        assertEquals(false, obj["is_read"]?.jsonPrimitive?.boolean)
        assertEquals(true, obj["is_starred"]?.jsonPrimitive?.boolean)
    }

    @Test
    fun `Req 2-5 updateState with both null throws FeedmanException and skips HTTP request`() = runTest {
        // Arrange
        val repo = newRepository()

        // Act + Assert: Req 2.5 — 両 null はバリデーションエラーで FeedmanException
        val ex = captureFeedmanException {
            repo.updateState(itemId = "X", isRead = null, isStarred = null)
        }
        assertEquals(FeedmanException.CODE_UNKNOWN_ERROR, ex.code)
        assertEquals(
            "Req 2.5: 両 null のときはサーバーへリクエストを送信しない",
            0,
            server.requestCount,
        )
    }

    @Test
    fun `Req 2-6 updateState returns normally when server responds with 2xx success`() = runTest {
        // Arrange: 200 + 記事スナップショットを返す（FeedmanApi の戻り値型は CrossFeedItem だが、
        // 本リポジトリの呼び出し元は戻り値を見ず、例外が出ないことが Req 2.6 の「成功通知」）。
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(FixtureLoader.load("cross_feed_item.json")),
        )
        val repo = newRepository()

        // Act
        repo.updateState(itemId = "X", isRead = true, isStarred = null)

        // Assert: HTTP リクエストが 1 回行われ、例外が起きていない
        assertEquals(1, server.requestCount)
    }

    // ===== Requirement 3: エラー伝搬 =========================================

    @Test
    fun `Req 3-1 getItem 4xx response surfaces FeedmanException with code and message`() = runTest {
        // Arrange: SPEC §4.3 のエラーボディ
        val errorBody = """
            {"error":{"code":"NOT_FOUND","message":"記事が見つかりません","category":"client","action":"none"}}
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(404).setBody(errorBody))
        val repo = newRepository()

        // Act + Assert: Req 3.1 — FeedmanException として透過、code / message を保持
        val ex = captureFeedmanException { repo.getItem("X") }
        assertEquals("NOT_FOUND", ex.code)
        assertEquals("記事が見つかりません", ex.errorMessage)
        assertEquals(404, ex.httpStatus)
    }

    @Test
    fun `Req 3-2 updateState 5xx response surfaces FeedmanException with code and message`() = runTest {
        // Arrange: 5xx のエラーボディ
        val errorBody = """
            {"error":{"code":"INTERNAL","message":"サーバーエラー","category":"server","action":"retry"}}
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(500).setBody(errorBody))
        val repo = newRepository()

        // Act + Assert: Req 3.2 — FeedmanException として透過
        val ex = captureFeedmanException {
            repo.updateState(itemId = "X", isRead = true, isStarred = null)
        }
        assertEquals("INTERNAL", ex.code)
        assertEquals("サーバーエラー", ex.errorMessage)
        assertEquals(500, ex.httpStatus)
    }

    @Test
    fun `Req 3-3 network failure during getItem surfaces FeedmanException with NETWORK_ERROR code`() = runTest {
        // Arrange: 接続切断で I/O 失敗を再現
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val repo = newRepository()

        // Act + Assert: Req 3.3 — NETWORK_ERROR コードで FeedmanException
        val ex = captureFeedmanException { repo.getItem("X") }
        assertEquals(FeedmanException.CODE_NETWORK_ERROR, ex.code)
    }

    @Test
    fun `Req 3-3 network failure during updateState surfaces FeedmanException with NETWORK_ERROR code`() = runTest {
        // Arrange
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val repo = newRepository()

        // Act + Assert
        val ex = captureFeedmanException {
            repo.updateState(itemId = "X", isRead = true, isStarred = null)
        }
        assertEquals(FeedmanException.CODE_NETWORK_ERROR, ex.code)
    }
}
