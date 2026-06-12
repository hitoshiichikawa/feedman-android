package com.feedman.android.core.auth

import com.feedman.android.core.auth.fake.InMemoryTokenStore
import com.feedman.android.core.data.UserRepositoryImpl
import com.feedman.android.core.network.ApiClientFactory
import com.feedman.android.core.network.FeedmanApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * [AuthRepositoryImpl] の単体テスト（Issue #21 requirements.md）。
 *
 * MockWebServer + 実 Retrofit/OkHttp + InMemoryTokenStore を組み合わせ、AC ごとの観測可能な
 * 結果を検証する（CLAUDE.md テスト規約: Retrofit / OkHttp / TokenStore をモックしない）。
 *
 * 対応 AC:
 * - Req 1.1 / 1.2: exchange 成功時に TokenStore へ保存 + リクエスト body に auth_code / code_verifier
 * - Req 1.3: exchange サーバーエラー時に TokenStore を書き換えない
 * - Req 1.4: exchange ネットワーク失敗時に TokenStore 既存内容を維持
 * - Req 2.1 / 2.2: refresh 成功時にローテーション結果で上書き
 * - Req 2.3 / NFR 2: refresh 並行呼び出しが単一リクエストに集約
 * - Req 2.4: INVALID_REFRESH_TOKEN で TokenStore 消去 + AuthRequired
 * - Req 2.5: refresh ネットワーク失敗時に TokenStore 維持
 * - Req 2.6: refresh token 未保存時にネットワーク発行せず AuthRequired
 * - Req 3.1 / 3.2: revoke 完了で TokenStore 消去（best-effort）
 * - Req 3.3: refresh token 未保存時はネットワーク発行せず TokenStore 消去のみ
 * - Req 5.1 / 5.2: currentUser は me エンドポイントを呼び、エラーを呼び出し元へ伝搬
 * - NFR 3.1: 観測可能なログイン状態（observeIsAuthenticated）が遷移する
 */
class AuthRepositoryImplTest {

    private lateinit var server: MockWebServer
    private lateinit var baseUrl: String
    private lateinit var tokenStore: InMemoryTokenStore
    private lateinit var api: FeedmanApi
    private lateinit var repo: AuthRepositoryImpl

    private val fixedClock: Clock = Clock.fixed(Instant.ofEpochMilli(1_000_000L), ZoneOffset.UTC)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        baseUrl = server.url("/").toString()
        tokenStore = InMemoryTokenStore()
        // AuthInterceptor も組み込み、本番に近い経路で検証する。
        api = ApiClientFactory.create(
            baseUrl = baseUrl,
            additionalInterceptors = listOf(AuthInterceptor(tokenStore)),
        )
        val userRepo = UserRepositoryImpl(api)
        repo = AuthRepositoryImpl(
            api = api,
            tokenStore = tokenStore,
            userRepository = userRepo,
            clock = fixedClock,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun successTokenBody(
        accessToken: String = "new-access",
        refreshToken: String = "new-refresh",
        expiresIn: Long = 900,
    ): String = """
        {"access_token":"$accessToken","refresh_token":"$refreshToken","token_type":"Bearer","expires_in":$expiresIn}
    """.trimIndent()

    // ===== Requirement 1: exchange =====

    @Test
    fun `Req 1-1 exchange success persists TokenSet to TokenStore`() = runTest {
        // Arrange
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                successTokenBody(accessToken = "AT", refreshToken = "RT", expiresIn = 900),
            ),
        )

        // Act
        val result = repo.exchange(authCode = "code-1", codeVerifier = "verifier-1")

        // Assert: 結果 Success かつ TokenStore に保存される
        assertEquals(ExchangeResult.Success, result)
        val saved = tokenStore.read()
        assertNotNull(saved)
        assertEquals("AT", saved!!.accessToken)
        assertEquals("RT", saved.refreshToken)
        // fixedClock(1_000_000ms) + expires_in(900s = 900_000ms) = 1_900_000ms
        assertEquals(1_900_000L, saved.accessTokenExpiresAtEpochMillis)
    }

    @Test
    fun `Req 1-2 exchange sends auth_code and code_verifier in request body`() = runTest {
        // Arrange
        server.enqueue(MockResponse().setResponseCode(200).setBody(successTokenBody()))

        // Act
        repo.exchange(authCode = "code-X", codeVerifier = "verifier-Y")

        // Assert
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/auth/token", recorded.requestUrl?.encodedPath)
        val body = recorded.body.readUtf8()
        // body は JSON。auth_code と code_verifier 両フィールドを含む
        assertTrue("auth_code missing: $body", body.contains("\"auth_code\":\"code-X\""))
        assertTrue("code_verifier missing: $body", body.contains("\"code_verifier\":\"verifier-Y\""))
    }

    @Test
    fun `Req 1-3 exchange server error does not write TokenStore`() = runTest {
        // Arrange: 既存トークンを保存しておく
        val existing = TokenSet("existing-AT", "existing-RT", 50_000L)
        tokenStore.save(existing)
        val errorBody = """
            {"error":{"code":"INVALID_GRANT","message":"code expired","category":"auth","action":"login"}}
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(400).setBody(errorBody))

        // Act
        val result = repo.exchange(authCode = "code", codeVerifier = "verifier")

        // Assert: ServerError 返却 / TokenStore は既存のまま（書き換えなし）
        assertTrue("expected ServerError but got $result", result is ExchangeResult.ServerError)
        val server = result as ExchangeResult.ServerError
        assertEquals("INVALID_GRANT", server.code)
        assertEquals(400, server.httpStatus)
        assertEquals(existing, tokenStore.read())
    }

    @Test
    fun `Req 1-4 exchange network failure preserves TokenStore`() = runTest {
        // Arrange
        val existing = TokenSet("existing-AT", "existing-RT", 50_000L)
        tokenStore.save(existing)
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        // Act
        val result = repo.exchange(authCode = "code", codeVerifier = "verifier")

        // Assert
        assertTrue("expected NetworkFailure but got $result", result is ExchangeResult.NetworkFailure)
        assertEquals(existing, tokenStore.read())
    }

    // ===== Requirement 2: refresh =====

    @Test
    fun `Req 2-1 Req 2-2 refresh rotates TokenSet using stored refresh token`() = runTest {
        // Arrange: 既存トークンを保存
        tokenStore.save(TokenSet("old-AT", "old-RT", 0L))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                successTokenBody(accessToken = "rotated-AT", refreshToken = "rotated-RT", expiresIn = 900),
            ),
        )

        // Act
        val result = repo.refresh()

        // Assert
        assertEquals(RefreshResult.Success, result)
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/auth/refresh", recorded.requestUrl?.encodedPath)
        // body に古い refresh token が含まれる（Req 2.1）
        assertTrue(recorded.body.readUtf8().contains("\"refresh_token\":\"old-RT\""))
        // TokenStore は新値で上書き（Req 2.2）
        val saved = tokenStore.read()!!
        assertEquals("rotated-AT", saved.accessToken)
        assertEquals("rotated-RT", saved.refreshToken)
    }

    @Test
    fun `Req 2-3 NFR 2 concurrent refresh calls share single network request and identical result`() = runTest {
        // Arrange: 既存トークンを保存
        tokenStore.save(TokenSet("old-AT", "old-RT", 0L))
        // refresh エンドポイントに 1 つだけレスポンスを enqueue する。
        // 2 つ目のリクエストが来たら MockWebServer は応答を返せずタイムアウトするはずだが、
        // 単一飛行が機能していれば 1 リクエストだけが発行され、両 await が同じ結果を共有する。
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    successTokenBody(accessToken = "shared-AT", refreshToken = "shared-RT"),
                )
                .setBodyDelay(200, java.util.concurrent.TimeUnit.MILLISECONDS),
        )

        // Act: 並行に refresh を 5 回呼ぶ
        val results = coroutineScope {
            val deferreds = List(5) {
                async { repo.refresh() }
            }
            deferreds.awaitAll()
        }

        // Assert
        // (1) 全結果が Success かつ同一
        results.forEach { assertEquals(RefreshResult.Success, it) }
        // (2) ネットワークリクエストは 1 つだけ発行された
        assertEquals(
            "single-flight: only one /api/auth/refresh should be sent",
            1,
            server.requestCount,
        )
        // (3) TokenStore は rotation 結果に更新されている
        val saved = tokenStore.read()!!
        assertEquals("shared-AT", saved.accessToken)
        assertEquals("shared-RT", saved.refreshToken)
    }

    @Test
    fun `Req 2-4 INVALID_REFRESH_TOKEN clears TokenStore and returns AuthRequired`() = runTest {
        // Arrange
        tokenStore.save(TokenSet("old-AT", "old-RT", 0L))
        val errorBody = """
            {"error":{"code":"INVALID_REFRESH_TOKEN","message":"refresh expired","category":"auth","action":"login"}}
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(401).setBody(errorBody))

        // Act
        val result = repo.refresh()

        // Assert: TokenStore 消去 + AuthRequired
        assertEquals(RefreshResult.AuthRequired, result)
        assertNull(tokenStore.read())
        // observeIsAuthenticated も false に遷移（NFR 3.1）
        assertEquals(false, repo.observeIsAuthenticated().value)
    }

    @Test
    fun `Req 2-5 refresh network failure preserves TokenStore`() = runTest {
        // Arrange
        val stored = TokenSet("keep-AT", "keep-RT", 0L)
        tokenStore.save(stored)
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        // Act
        val result = repo.refresh()

        // Assert
        assertTrue("expected NetworkFailure but got $result", result is RefreshResult.NetworkFailure)
        assertEquals(stored, tokenStore.read())
    }

    @Test
    fun `Req 2-6 refresh without stored refresh token does not issue network request and returns AuthRequired`() =
        runTest {
            // Arrange: TokenStore は empty
            assertNull(tokenStore.read())

            // Act
            val result = repo.refresh()

            // Assert
            assertEquals(RefreshResult.AuthRequired, result)
            assertEquals(
                "no network request should be issued when no refresh token is stored",
                0,
                server.requestCount,
            )
        }

    // ===== Requirement 3: revoke =====

    @Test
    fun `Req 3-1 Req 3-2 revoke success clears TokenStore`() = runTest {
        // Arrange
        tokenStore.save(TokenSet("AT", "RT", 0L))
        server.enqueue(MockResponse().setResponseCode(204))

        // Act
        repo.revoke()

        // Assert
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/auth/revoke", recorded.requestUrl?.encodedPath)
        assertTrue(recorded.body.readUtf8().contains("\"refresh_token\":\"RT\""))
        assertNull(tokenStore.read())
        assertEquals(false, repo.observeIsAuthenticated().value)
    }

    @Test
    fun `Req 3-2 revoke server error still clears TokenStore (best effort)`() = runTest {
        // Arrange
        tokenStore.save(TokenSet("AT", "RT", 0L))
        val errorBody = """
            {"error":{"code":"INTERNAL","message":"server error","category":"server","action":"retry"}}
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(500).setBody(errorBody))

        // Act
        repo.revoke()

        // Assert: 5xx でも消去
        assertNull(tokenStore.read())
    }

    @Test
    fun `Req 3-2 revoke network failure still clears TokenStore (best effort)`() = runTest {
        // Arrange
        tokenStore.save(TokenSet("AT", "RT", 0L))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        // Act
        repo.revoke()

        // Assert
        assertNull(tokenStore.read())
    }

    @Test
    fun `Req 3-3 revoke without stored refresh token issues no network request but clears store`() =
        runTest {
            // Arrange: TokenStore は empty
            assertNull(tokenStore.read())

            // Act
            repo.revoke()

            // Assert
            assertEquals(
                "no network request should be issued when no refresh token is stored",
                0,
                server.requestCount,
            )
            assertNull(tokenStore.read())
            assertEquals(false, repo.observeIsAuthenticated().value)
        }

    // ===== Requirement 5: currentUser =====

    @Test
    fun `Req 5-1 currentUser fetches me endpoint and returns user`() = runTest {
        // Arrange
        tokenStore.save(TokenSet("AT", "RT", 0L))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"id":"u-1","email":"alice@example.com"}"""
            ),
        )

        // Act
        val result = repo.currentUser()

        // Assert
        assertTrue("expected Success but got $result", result is CurrentUserResult.Success)
        val user = (result as CurrentUserResult.Success).user
        assertEquals("u-1", user.id)
        assertEquals("alice@example.com", user.email)
        // Bearer が付与されている（Req 4.1 経路の確認）
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/auth/me", recorded.requestUrl?.encodedPath)
        assertEquals("Bearer AT", recorded.getHeader("Authorization"))
    }

    @Test
    fun `Req 5-2 currentUser propagates auth error to caller without clearing TokenStore`() = runTest {
        // Arrange
        val stored = TokenSet("AT", "RT", 0L)
        tokenStore.save(stored)
        val errorBody = """
            {"error":{"code":"UNAUTHORIZED","message":"authentication required","category":"auth","action":"login"}}
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(401).setBody(errorBody))

        // Act
        val result = repo.currentUser()

        // Assert: Failure として伝搬 / TokenStore は本機能では消さない（Req 5.2）
        assertTrue("expected Failure but got $result", result is CurrentUserResult.Failure)
        val failure = result as CurrentUserResult.Failure
        assertEquals("UNAUTHORIZED", failure.code)
        assertEquals(401, failure.httpStatus)
        assertEquals(stored, tokenStore.read())
    }

    @Test
    fun `Req 5 currentUser network failure surfaces NetworkFailure`() = runTest {
        // Arrange
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        // Act
        val result = repo.currentUser()

        // Assert
        assertTrue("expected NetworkFailure but got $result", result is CurrentUserResult.NetworkFailure)
    }

    // ===== NFR 3: 観測可能なログイン状態 =====

    @Test
    fun `NFR 3-1 observeIsAuthenticated transitions to true after exchange success`() = runTest {
        // Arrange
        server.enqueue(MockResponse().setResponseCode(200).setBody(successTokenBody()))
        assertEquals(false, repo.observeIsAuthenticated().value)

        // Act
        repo.exchange(authCode = "c", codeVerifier = "v")

        // Assert
        assertEquals(true, repo.observeIsAuthenticated().value)
    }

    @Test
    fun `NFR 3-1 observeIsAuthenticated transitions to false after revoke`() = runTest {
        // Arrange
        tokenStore.save(TokenSet("AT", "RT", 0L))
        server.enqueue(MockResponse().setResponseCode(204))
        // 初期反映
        repo.refreshAuthenticatedState()
        assertEquals(true, repo.observeIsAuthenticated().value)

        // Act
        repo.revoke()

        // Assert
        assertEquals(false, repo.observeIsAuthenticated().value)
    }
}
