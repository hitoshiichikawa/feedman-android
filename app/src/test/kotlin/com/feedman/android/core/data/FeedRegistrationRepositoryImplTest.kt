package com.feedman.android.core.data

import com.feedman.android.core.model.FixtureLoader
import com.feedman.android.core.network.ApiClientFactory
import com.feedman.android.core.network.FeedmanException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [FeedRegistrationRepositoryImpl] の単体テスト（Issue #44 / requirements.md AC 3.1, 5.x）。
 *
 * MockWebServer で実 HTTP レスポンスを返し、`FeedmanApi` をモックしない（CLAUDE.md テスト規約）。
 *
 * 観点:
 * - POST `/api/feeds` のメソッド / パス / ボディ（url フィールド）
 * - 成功時の Subscription decode
 * - 失敗時の例外型 / code / httpStatus / retryAfterSeconds 伝搬
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedRegistrationRepositoryImplTest {

    private lateinit var server: MockWebServer
    private lateinit var baseUrl: String

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = MockWebServer()
        server.start()
        baseUrl = server.url("/").toString()
    }

    @After
    fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    private fun newRepository(): FeedRegistrationRepositoryImpl {
        val api = ApiClientFactory.create(baseUrl = baseUrl)
        return FeedRegistrationRepositoryImpl(api)
    }

    // ===== Requirement 3.1: 登録要求の送信 =====

    @Test
    fun `Req 3_1 register で api feeds エンドポイントを POST し url フィールドを含む JSON ボディを送る`() = runTest {
        // Arrange
        val active = FixtureLoader.load("subscription_active.json")
        server.enqueue(MockResponse().setResponseCode(201).setBody(active))
        val repo = newRepository()

        // Act
        repo.register("https://example.com/feed.xml")

        // Assert: メソッドとパス
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/feeds", recorded.requestUrl?.encodedPath)
        // ボディに url キーと値が含まれる
        val body = recorded.body.readUtf8()
        assertTrue("body should contain url key: $body", body.contains("\"url\""))
        assertTrue("body should contain target url: $body", body.contains("https://example.com/feed.xml"))
    }

    @Test
    fun `Req 3_1 register 成功で 201 応答を Subscription として decode する`() = runTest {
        // Arrange
        val active = FixtureLoader.load("subscription_active.json")
        server.enqueue(MockResponse().setResponseCode(201).setBody(active))
        val repo = newRepository()

        // Act
        val returned = repo.register("https://example.com/feed.xml")

        // Assert
        assertEquals("Feedman Dev Blog", returned.feedTitle)
        assertEquals("active", returned.feedStatus)
        assertEquals("01HGY8K9ZQ4N7TXVY1F8M9R3FE", returned.feedId)
    }

    // ===== Requirement 5: エラー応答の透過 =====

    @Test
    fun `Req 5_1 409 重複登録時に httpStatus 409 を持つ FeedmanException を投げる`() = runTest {
        // Arrange: 409 Conflict 応答
        val errorBody = """
            {
              "error": {
                "code": "FEED_ALREADY_REGISTERED",
                "message": "このフィードはすでに登録されています。"
              }
            }
        """.trimIndent()
        server.enqueue(
            MockResponse().setResponseCode(409)
                .setHeader("Content-Type", "application/json")
                .setBody(errorBody),
        )
        val repo = newRepository()

        // Act
        var thrown: Throwable? = null
        try {
            repo.register("https://example.com/feed.xml")
        } catch (e: Throwable) {
            thrown = e
        }

        // Assert
        assertTrue("expected FeedmanException, got $thrown", thrown is FeedmanException)
        val fe = thrown as FeedmanException
        assertEquals(409, fe.httpStatus)
        assertEquals("このフィードはすでに登録されています。", fe.errorMessage)
    }

    @Test
    fun `Req 5_3 429 レート制限時に retryAfterSeconds を持つ FeedmanException を投げる`() = runTest {
        // Arrange: 429 + details.retry_after_seconds
        val errorBody = """
            {
              "error": {
                "code": "REGISTRATION_RATE_LIMIT",
                "message": "登録のレート制限中です。",
                "details": { "retry_after_seconds": 60 }
              }
            }
        """.trimIndent()
        server.enqueue(
            MockResponse().setResponseCode(429)
                .setHeader("Content-Type", "application/json")
                .setBody(errorBody),
        )
        val repo = newRepository()

        // Act
        var thrown: Throwable? = null
        try {
            repo.register("https://example.com/feed.xml")
        } catch (e: Throwable) {
            thrown = e
        }

        // Assert
        assertTrue("expected FeedmanException, got $thrown", thrown is FeedmanException)
        val fe = thrown as FeedmanException
        assertEquals(429, fe.httpStatus)
        assertEquals(60, fe.retryAfterSeconds)
    }

    @Test
    fun `Req 5_2 400 URL 不正時に httpStatus 400 を持つ FeedmanException を投げる`() = runTest {
        // Arrange
        val errorBody = """
            {
              "error": {
                "code": "INVALID_FEED_URL",
                "message": "このサイトでフィードを検出できませんでした。"
              }
            }
        """.trimIndent()
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody(errorBody),
        )
        val repo = newRepository()

        // Act
        var thrown: Throwable? = null
        try {
            repo.register("https://example.com/not-a-feed")
        } catch (e: Throwable) {
            thrown = e
        }

        // Assert
        assertTrue("expected FeedmanException, got $thrown", thrown is FeedmanException)
        val fe = thrown as FeedmanException
        assertEquals(400, fe.httpStatus)
    }

    @Test
    fun `Req 5_6 ネットワーク失敗時に NETWORK_ERROR の FeedmanException を投げる`() = runTest {
        // Arrange
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val repo = newRepository()

        // Act
        var thrown: Throwable? = null
        try {
            repo.register("https://example.com/feed.xml")
        } catch (e: Throwable) {
            thrown = e
        }

        // Assert
        assertTrue("expected FeedmanException, got $thrown", thrown is FeedmanException)
        val fe = thrown as FeedmanException
        assertEquals(FeedmanException.CODE_NETWORK_ERROR, fe.code)
    }
}
