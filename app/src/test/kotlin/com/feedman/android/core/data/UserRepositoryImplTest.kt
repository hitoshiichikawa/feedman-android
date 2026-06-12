package com.feedman.android.core.data

import com.feedman.android.core.model.FixtureLoader
import com.feedman.android.core.network.ApiClientFactory
import com.feedman.android.core.network.FeedmanException
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * [UserRepositoryImpl] の AC 単位検証（Issue #49 requirements.md）。
 *
 * MockWebServer で実 HTTP 経路を再現し、`GET /auth/me` の契約（パス・メソッド・
 * decode・エラー透過）を検証する。Retrofit / OkHttp / kotlinx.serialization は実物を
 * 使い、`FeedmanApi` をモックしない（CLAUDE.md テスト規約）。
 *
 * 対応 AC:
 * - Req 1.2: アカウントシート起動時に 1 回 GET /auth/me を発行
 * - Req 2.1: email を含む User として decode
 * - Req 4.1: ネットワークエラーを FeedmanException として透過
 * - Req 5.1: 認証エラー（UNAUTHORIZED）を FeedmanException として透過（code 保持）
 */
class UserRepositoryImplTest {

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

    private fun newRepository(): UserRepositoryImpl {
        val api = ApiClientFactory.create(baseUrl = baseUrl)
        return UserRepositoryImpl(api)
    }

    private suspend fun captureFeedmanException(block: suspend () -> Unit): FeedmanException {
        try {
            block()
        } catch (e: FeedmanException) {
            return e
        }
        fail("Expected FeedmanException, but block returned normally")
        error("unreachable")
    }

    @Test
    fun `Req 1-2 getCurrentUser issues GET to auth me`() = runTest {
        // Arrange
        val body = FixtureLoader.load("user.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val repo = newRepository()

        // Act
        repo.getCurrentUser()

        // Assert
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/auth/me", recorded.requestUrl?.encodedPath)
    }

    @Test
    fun `Req 2-1 getCurrentUser decodes 200 response into User with email`() = runTest {
        // Arrange
        val body = FixtureLoader.load("user.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val repo = newRepository()

        // Act
        val user = repo.getCurrentUser()

        // Assert: id と email が User に decode される（Req 2.1）
        assertEquals("01HGY8K9ZQ4N7TXVY1F8M9R3US", user.id)
        assertEquals("alice@example.com", user.email)
    }

    @Test
    fun `Req 2-2 getCurrentUser decodes empty email as empty string`() = runTest {
        // Arrange: email が空文字（Req 2.2 の境界）
        val body = """{"id":"01HGY8K9ZQ4N7TXVY1F8M9R3US","email":""}"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val repo = newRepository()

        // Act
        val user = repo.getCurrentUser()

        // Assert: Req 2.2 — 空文字 email も decode 自体は成功し、UI 側で代替文言を選ぶ責務
        assertEquals("", user.email)
    }

    @Test
    fun `Req 4-1 network failure surfaces FeedmanException with NETWORK_ERROR code`() = runTest {
        // Arrange: 接続切断で I/O 失敗を再現
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val repo = newRepository()

        // Act + Assert: Req 4.1 — NETWORK_ERROR コードで FeedmanException
        val ex = captureFeedmanException { repo.getCurrentUser() }
        assertEquals(FeedmanException.CODE_NETWORK_ERROR, ex.code)
    }

    @Test
    fun `Req 4-1 5xx response surfaces FeedmanException with server code`() = runTest {
        // Arrange: SPEC §4.3 のエラーボディ
        val errorBody = """
            {"error":{"code":"INTERNAL","message":"サーバーエラー","category":"server","action":"retry"}}
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(500).setBody(errorBody))
        val repo = newRepository()

        // Act + Assert: Req 4.1 — FeedmanException として透過、code / message を保持
        val ex = captureFeedmanException { repo.getCurrentUser() }
        assertEquals("INTERNAL", ex.code)
        assertEquals("サーバーエラー", ex.errorMessage)
        assertEquals(500, ex.httpStatus)
    }

    @Test
    fun `Req 5-1 401 UNAUTHORIZED response surfaces FeedmanException with UNAUTHORIZED code`() =
        runTest {
            // Arrange: SPEC §4.3 認証切れ応答
            val errorBody = """
                {"error":{"code":"UNAUTHORIZED","message":"認証が必要です","category":"auth","action":"login"}}
            """.trimIndent()
            server.enqueue(MockResponse().setResponseCode(401).setBody(errorBody))
            val repo = newRepository()

            // Act + Assert: Req 5.1 — UNAUTHORIZED code を保持した FeedmanException
            val ex = captureFeedmanException { repo.getCurrentUser() }
            assertEquals("UNAUTHORIZED", ex.code)
            assertEquals(401, ex.httpStatus)
        }
}
