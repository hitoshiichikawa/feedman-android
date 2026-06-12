package com.feedman.android.core.network

import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.net.HttpURLConnection
import java.util.concurrent.atomic.AtomicInteger

/**
 * [FeedmanApi] / [ApiClientFactory] / [FeedmanErrorMappingInterceptor] / [FeedmanApiProxy] の
 * 統合的な動作検証。
 *
 * Issue #17 の AC（Requirements 1-4 / NFR）と 1 対 1 に対応するテストを並べる。
 * Retrofit インターフェースをモックせず、MockWebServer から実 HTTP レスポンスを返して
 * 検証する（CLAUDE.md テスト規約 / NFR 1.2）。
 *
 * fixture は `app/src/test/resources/fixtures/`（Issue #15 / #16 で配置済み）を再利用する
 * （NFR 1.3）。
 */
class FeedmanApiTest {

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

    // ===== Requirement 1: FeedmanApi（Retrofit インターフェース）=====

    @Test
    fun `Req 1-1 cross-feed endpoint decodes 200 response to CrossFeedPage`() = runTest {
        // Arrange
        val body = FixtureLoader.load("cross_feed_page.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val api = ApiClientFactory.create(baseUrl = baseUrl)

        // Act
        val page = api.getCrossFeed()

        // Assert
        assertEquals(2, page.items.size)
        assertEquals("2026-06-12T09:30:00Z", page.sinceTime)
        assertTrue(page.hasMore)
        // 実際に GET /api/items/cross-feed が叩かれていることをパスで確認
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/api/items/cross-feed", recorded.requestUrl?.encodedPath)
    }

    @Test
    fun `Req 1-2 cross-feed accepts cursor and limit query parameters`() = runTest {
        // Arrange
        val body = FixtureLoader.load("cross_feed_page_terminal.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val api = ApiClientFactory.create(baseUrl = baseUrl)

        // Act
        api.getCrossFeed(cursor = "abc:01HGY", limit = 50)

        // Assert: クエリパラメータが正しく付与される
        val recorded = server.takeRequest()
        assertEquals("abc:01HGY", recorded.requestUrl?.queryParameter("cursor"))
        assertEquals("50", recorded.requestUrl?.queryParameter("limit"))
    }

    @Test
    fun `Req 1-3 feed items endpoint decodes Page of ItemSummary`() = runTest {
        // Arrange
        val body = FixtureLoader.load("item_summary_page_has_more.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val api = ApiClientFactory.create(baseUrl = baseUrl)

        // Act
        val page = api.getFeedItems(feedId = "01HGY8K9ZQ4N7TXVY1F8M9R3FE", filter = "all")

        // Assert
        assertEquals(2, page.items.size)
        assertEquals("ページ 1 の記事 A", page.items[0].title)
        // hatebu_fetched_at が null のケースも nullable へ正しくマップ（Req 2.3）
        assertNull(page.items[1].hatebuFetchedAt)
        val recorded = server.takeRequest()
        assertEquals("/api/feeds/01HGY8K9ZQ4N7TXVY1F8M9R3FE/items", recorded.requestUrl?.encodedPath)
        assertEquals("all", recorded.requestUrl?.queryParameter("filter"))
    }

    @Test
    fun `Req 1-3 item detail endpoint decodes ItemDetail with content and author`() = runTest {
        // Arrange
        val body = FixtureLoader.load("item_detail.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val api = ApiClientFactory.create(baseUrl = baseUrl)

        // Act
        val detail = api.getItem(itemId = "01HGY8K9ZQ4N7TXVY1F8M9R3D3")

        // Assert
        assertEquals("詳細記事のサンプル", detail.title)
        assertEquals("Feedman Test", detail.author)
        assertTrue(detail.content.contains("sanitized"))
    }

    @Test
    fun `Req 1-3 subscriptions list endpoint decodes List of Subscription`() = runTest {
        // Arrange: subscription_active.json は単体オブジェクトなので配列にラップ
        val singleBody = FixtureLoader.load("subscription_active.json")
        val arrayBody = "[$singleBody]"
        server.enqueue(MockResponse().setResponseCode(200).setBody(arrayBody))
        val api = ApiClientFactory.create(baseUrl = baseUrl)

        // Act
        val subs = api.getSubscriptions()

        // Assert
        assertEquals(1, subs.size)
        assertEquals("active", subs[0].feedStatus)
        // Req 2.3: error_message が null（subscription_active.json）でも nullable へマップ
        assertNull(subs[0].errorMessage)
    }

    @Test
    fun `Req 1-3 user endpoint decodes User ignoring server-side extra fields`() = runTest {
        // Arrange: user.json は display_name / created_at の未知フィールドを含む
        val body = FixtureLoader.load("user.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val api = ApiClientFactory.create(baseUrl = baseUrl)

        // Act
        val user = api.getCurrentUser()

        // Assert
        assertEquals("01HGY8K9ZQ4N7TXVY1F8M9R3US", user.id)
        assertEquals("alice@example.com", user.email)
    }

    @Test
    fun `Req 1-4 update item state accepts nullable is_read and is_starred fields`() = runTest {
        // Arrange: SPEC §4.2 の body 契約は { is_read?: bool|null, is_starred?: bool|null }。
        // 片方だけを更新する 2 パターンを順に送信し、どちらも PUT が成功することを確認する。
        val responseBody = FixtureLoader.load("cross_feed_item.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(responseBody))
        server.enqueue(MockResponse().setResponseCode(200).setBody(responseBody))
        val api = ApiClientFactory.create(baseUrl = baseUrl)

        // Act-1: is_read のみ送信（is_starred は null = 変更しない）
        api.updateItemState(
            itemId = "01HGY8K9ZQ4N7TXVY1F8M9R3X1",
            request = ItemStateUpdateRequest(isRead = true, isStarred = null),
        )
        val r1 = server.takeRequest()
        // Act-2: is_starred のみ送信（is_read は null）
        api.updateItemState(
            itemId = "01HGY8K9ZQ4N7TXVY1F8M9R3X1",
            request = ItemStateUpdateRequest(isRead = null, isStarred = true),
        )
        val r2 = server.takeRequest()

        // Assert: PUT メソッド・正しいパスが叩かれ、各 body に対応フィールドが含まれている
        assertEquals("PUT", r1.method)
        assertEquals(
            "/api/items/01HGY8K9ZQ4N7TXVY1F8M9R3X1/state",
            r1.requestUrl?.encodedPath,
        )
        val body1 = r1.body.readUtf8()
        assertTrue("body should include is_read=true: $body1", body1.contains("\"is_read\":true"))

        val body2 = r2.body.readUtf8()
        assertTrue("body should include is_starred=true: $body2", body2.contains("\"is_starred\":true"))
    }

    @Test
    fun `Req 1-4 update item state with both fields non-null sends both`() = runTest {
        // Arrange
        val responseBody = FixtureLoader.load("cross_feed_item.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(responseBody))
        val api = ApiClientFactory.create(baseUrl = baseUrl)

        // Act
        api.updateItemState(
            itemId = "ID1",
            request = ItemStateUpdateRequest(isRead = false, isStarred = true),
        )

        // Assert
        val recorded = server.takeRequest()
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"is_read\":false"))
        assertTrue(body.contains("\"is_starred\":true"))
    }

    @Test
    fun `Req 1-5 logout and delete user and update last seen all reach correct endpoints`() = runTest {
        // Arrange: 3 件の 204 No Content を順に積む
        repeat(3) {
            server.enqueue(MockResponse().setResponseCode(HttpURLConnection.HTTP_NO_CONTENT))
        }
        val api = ApiClientFactory.create(baseUrl = baseUrl)

        // Act
        api.logout()
        api.deleteCurrentUser()
        api.updateCrossFeedLastSeen()

        // Assert
        val r1 = server.takeRequest()
        assertEquals("POST", r1.method)
        assertEquals("/auth/logout", r1.requestUrl?.encodedPath)

        val r2 = server.takeRequest()
        assertEquals("DELETE", r2.method)
        assertEquals("/api/users/me", r2.requestUrl?.encodedPath)

        val r3 = server.takeRequest()
        assertEquals("PUT", r3.method)
        assertEquals("/api/users/me/cross-feed-last-seen", r3.requestUrl?.encodedPath)
    }

    @Test
    fun `Req 1-5 subscription fetch endpoint is POST and reaches correct path`() = runTest {
        // Arrange
        val body = FixtureLoader.load("subscription_active.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val api = ApiClientFactory.create(baseUrl = baseUrl)

        // Act
        api.fetchSubscription(subscriptionId = "01HGY8K9ZQ4N7TXVY1F8M9R3SU")

        // Assert
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals(
            "/api/subscriptions/01HGY8K9ZQ4N7TXVY1F8M9R3SU/fetch",
            recorded.requestUrl?.encodedPath,
        )
    }

    // ===== Requirement 2: ApiClientFactory =====

    @Test
    fun `Req 2-2 decoder ignores unknown JSON fields without throwing`() = runTest {
        // Arrange: item_summary_with_unknown_keys.json は未知トップキーを含む
        // それを Page にラップして返す
        val itemWithUnknown = FixtureLoader.load("item_summary_with_unknown_keys.json")
        val pageBody = """
            {
              "items": [$itemWithUnknown],
              "next_cursor": null,
              "has_more": false,
              "extra_root_field": "should be ignored"
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(pageBody))
        val api = ApiClientFactory.create(baseUrl = baseUrl)

        // Act
        val page = api.getFeedItems(feedId = "fid")

        // Assert
        assertEquals(1, page.items.size)
        assertEquals("未知のキーを含む記事", page.items[0].title)
    }

    @Test
    fun `Req 2-3 decoder maps explicit null to nullable property`() = runTest {
        // Arrange: subscription_error.json は favicon_url=null / error_message=非null を含む
        val singleBody = FixtureLoader.load("subscription_error.json")
        val arrayBody = "[$singleBody]"
        server.enqueue(MockResponse().setResponseCode(200).setBody(arrayBody))
        val api = ApiClientFactory.create(baseUrl = baseUrl)

        // Act
        val subs = api.getSubscriptions()

        // Assert
        assertNull(subs[0].faviconUrl)
        assertEquals("HTTP 503 Service Unavailable", subs[0].errorMessage)
    }

    @Test
    fun `Req 2-5 with no extra interceptor or authenticator the api still works`() = runTest {
        // Arrange
        val body = FixtureLoader.load("user.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))

        // Act
        val api = ApiClientFactory.create(
            baseUrl = baseUrl,
            additionalInterceptors = emptyList(),
            authenticator = null,
        )
        val user = api.getCurrentUser()

        // Assert
        assertEquals("alice@example.com", user.email)
    }

    @Test
    fun `Req 2-6 same input produces FeedmanApi with consistent endpoint contract`() = runTest {
        // Arrange: 2 回作成し、それぞれで同じエンドポイントが叩かれることを確認
        val body = FixtureLoader.load("user.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))

        // Act
        val api1 = ApiClientFactory.create(baseUrl = baseUrl)
        val api2 = ApiClientFactory.create(baseUrl = baseUrl)
        api1.getCurrentUser()
        api2.getCurrentUser()

        // Assert
        val r1 = server.takeRequest()
        val r2 = server.takeRequest()
        assertEquals(r1.requestUrl?.encodedPath, r2.requestUrl?.encodedPath)
        assertEquals("/auth/me", r1.requestUrl?.encodedPath)
    }

    // ===== Requirement 3: エラー応答の FeedmanException 変換 =====

    @Test
    fun `Req 3-2 non-2xx with standard error body throws FeedmanException with server fields`() = runTest {
        // Arrange
        val errorBody = FixtureLoader.load("error_invalid_request.json")
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody(errorBody),
        )
        val api = ApiClientFactory.create(baseUrl = baseUrl)

        // Act
        val ex = assertThrowsFeedmanException { api.getCurrentUser() }

        // Assert
        assertEquals("INVALID_REQUEST", ex.code)
        assertEquals("リクエストパラメータが不正です。", ex.errorMessage)
        assertEquals("client", ex.category)
        assertEquals("fix_input", ex.action)
        assertEquals(400, ex.httpStatus)
    }

    @Test
    fun `Req 3-3 429 with details retry_after_seconds is preserved on FeedmanException`() = runTest {
        // Arrange
        val errorBody = FixtureLoader.load("error_feed_cooldown.json")
        server.enqueue(
            MockResponse().setResponseCode(429)
                .setHeader("Content-Type", "application/json")
                .setBody(errorBody),
        )
        val api = ApiClientFactory.create(baseUrl = baseUrl)

        // Act
        val ex = assertThrowsFeedmanException {
            api.fetchSubscription(subscriptionId = "01HGY8K9ZQ4N7TXVY1F8M9R3SU")
        }

        // Assert
        assertEquals("FEED_COOLDOWN", ex.code)
        assertEquals(30, ex.retryAfterSeconds)
        assertEquals(429, ex.httpStatus)
    }

    @Test
    fun `Req 3-4 non-2xx with malformed body falls back to UNKNOWN_ERROR code`() = runTest {
        // Arrange: 不完全 JSON
        val errorBody = FixtureLoader.load("error_malformed.json")
        server.enqueue(
            MockResponse().setResponseCode(500)
                .setHeader("Content-Type", "application/json")
                .setBody(errorBody),
        )
        val api = ApiClientFactory.create(baseUrl = baseUrl)

        // Act
        val ex = assertThrowsFeedmanException { api.getCurrentUser() }

        // Assert
        assertEquals(FeedmanException.CODE_UNKNOWN_ERROR, ex.code)
        assertEquals(500, ex.httpStatus)
        assertTrue("fallback message must not be empty", ex.errorMessage.isNotBlank())
    }

    @Test
    fun `Req 3-5 IOException during request becomes NETWORK_ERROR FeedmanException`() = runTest {
        // Arrange: server を即時切断して IOException を引き起こす
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val api = ApiClientFactory.create(baseUrl = baseUrl)

        // Act
        val ex = assertThrowsFeedmanException { api.getCurrentUser() }

        // Assert
        assertEquals(FeedmanException.CODE_NETWORK_ERROR, ex.code)
        assertNull(ex.httpStatus)
        assertNotNull("IOException cause should be preserved", ex.cause)
    }

    @Test
    fun `Req 3-6 HTTP status code is exposed on FeedmanException for downstream branching`() = runTest {
        // Arrange
        val errorBody = FixtureLoader.load("error_invalid_request.json")
        server.enqueue(
            MockResponse().setResponseCode(503)
                .setHeader("Content-Type", "application/json")
                .setBody(errorBody),
        )
        val api = ApiClientFactory.create(baseUrl = baseUrl)

        // Act
        val ex = assertThrowsFeedmanException { api.getCurrentUser() }

        // Assert
        assertEquals(503, ex.httpStatus)
    }

    // ===== Requirement 4: 拡張点 =====

    @Test
    fun `Req 4-2 additional interceptor is invoked for each request`() = runTest {
        // Arrange
        val body = FixtureLoader.load("user.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val callCount = AtomicInteger(0)
        val countingInterceptor = Interceptor { chain ->
            callCount.incrementAndGet()
            chain.proceed(chain.request())
        }
        val api = ApiClientFactory.create(
            baseUrl = baseUrl,
            additionalInterceptors = listOf(countingInterceptor),
        )

        // Act
        api.getCurrentUser()

        // Assert
        assertEquals(1, callCount.get())
    }

    @Test
    fun `Req 4-1 interceptor can mutate request headers and they reach the server`() = runTest {
        // Arrange
        val body = FixtureLoader.load("user.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val headerAddingInterceptor = Interceptor { chain ->
            val req = chain.request().newBuilder()
                .header("X-Test-Header", "from-interceptor")
                .build()
            chain.proceed(req)
        }
        val api = ApiClientFactory.create(
            baseUrl = baseUrl,
            additionalInterceptors = listOf(headerAddingInterceptor),
        )

        // Act
        api.getCurrentUser()

        // Assert
        val recorded = server.takeRequest()
        assertEquals("from-interceptor", recorded.getHeader("X-Test-Header"))
    }

    @Test
    fun `Req 4-1 multiple interceptors are invoked in registration order`() = runTest {
        // Arrange
        val body = FixtureLoader.load("user.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val order = mutableListOf<String>()
        val a = Interceptor { chain ->
            order.add("A-before")
            val resp = chain.proceed(chain.request())
            order.add("A-after")
            resp
        }
        val b = Interceptor { chain ->
            order.add("B-before")
            val resp = chain.proceed(chain.request())
            order.add("B-after")
            resp
        }
        val api = ApiClientFactory.create(
            baseUrl = baseUrl,
            additionalInterceptors = listOf(a, b),
        )

        // Act
        api.getCurrentUser()

        // Assert: A → B → network → B-after → A-after の順
        assertEquals(listOf("A-before", "B-before", "B-after", "A-after"), order)
    }

    @Test
    fun `Req 4-4 error conversion still works even when an authenticator-like interceptor is present`() = runTest {
        // Arrange: 余計な interceptor が混ざっていても、非 2xx は FeedmanException に変換される
        val errorBody = FixtureLoader.load("error_feed_cooldown.json")
        server.enqueue(
            MockResponse().setResponseCode(429)
                .setHeader("Content-Type", "application/json")
                .setBody(errorBody),
        )
        val noopInterceptor = Interceptor { chain -> chain.proceed(chain.request()) }
        val api = ApiClientFactory.create(
            baseUrl = baseUrl,
            additionalInterceptors = listOf(noopInterceptor),
        )

        // Act
        val ex = assertThrowsFeedmanException {
            api.fetchSubscription(subscriptionId = "abc")
        }

        // Assert
        assertEquals("FEED_COOLDOWN", ex.code)
        assertEquals(30, ex.retryAfterSeconds)
    }

    // ===== ヘルパー =====

    private inline fun assertThrowsFeedmanException(block: () -> Unit): FeedmanException {
        try {
            block()
        } catch (e: FeedmanException) {
            return e
        } catch (other: Throwable) {
            fail("Expected FeedmanException but got ${other::class.java.name}: ${other.message}")
            throw IllegalStateException("unreachable")
        }
        fail("Expected FeedmanException to be thrown")
        throw IllegalStateException("unreachable")
    }
}

