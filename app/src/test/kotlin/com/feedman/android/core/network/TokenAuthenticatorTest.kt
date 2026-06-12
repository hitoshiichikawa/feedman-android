package com.feedman.android.core.network

import com.feedman.android.core.auth.AuthInterceptor
import com.feedman.android.core.auth.AuthRepositoryImpl
import com.feedman.android.core.auth.TokenSet
import com.feedman.android.core.auth.fake.InMemoryTokenStore
import com.feedman.android.core.data.UserRepositoryImpl
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
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
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * [TokenAuthenticator] の単体テスト（Issue #22 requirements.md）。
 *
 * MockWebServer + 実 OkHttp/Retrofit + 実 AuthRepositoryImpl + InMemoryTokenStore で本番に
 * 近い経路を組み立て、受け入れ基準ごとに観測可能な結果を検証する（CLAUDE.md テスト規約:
 * Retrofit / OkHttp / TokenStore / AuthRepository をモックしない）。
 *
 * 対応 AC:
 * - Req 1.1 / 1.2 / NFR 2.1: 401 受信 → refresh 成功 → 新 access token で元リクエストを 1 回再試行
 * - Req 1.3 / NFR 2.1: 再試行が 401 以外のエラーを返した場合、その応答を呼び出し元に伝搬
 * - Req 1.4 / NFR 1.1 / NFR 1.2: 再試行は 1 回限り（refresh 後も 401 → 2 回目の refresh を行わない）
 * - Req 2.1: refresh token 未保存時に refresh 呼び出しを発行せず 401 を呼び出し元に伝搬
 * - Req 2.2 / 2.3 / 2.4: refresh 失敗時に TokenStore 消去 + observeIsAuthenticated=false +
 *   元リクエスト呼び出し元に 401 を伝搬
 * - Req 3.1 / 3.2 / 3.4 / NFR 2.1: 並行 401 → refresh は 1 回 → 全件再試行
 * - 認証エンドポイント（/api/auth/refresh）の 401 は authenticator の対象外（401 ループ防止）
 */
class TokenAuthenticatorTest {

    private lateinit var server: MockWebServer
    private lateinit var baseUrl: String
    private lateinit var tokenStore: InMemoryTokenStore
    private lateinit var api: FeedmanApi
    private lateinit var authRepository: AuthRepositoryImpl

    private val fixedClock: Clock = Clock.fixed(Instant.ofEpochMilli(1_000_000L), ZoneOffset.UTC)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        baseUrl = server.url("/").toString()
        tokenStore = InMemoryTokenStore()

        // 1) AuthRepositoryImpl 単体（refresh をサーバーへ送る用）には authenticator を組み込まない
        //    refresh エンドポイント自身を対象とすると 401 で無限ループするためで、本番は
        //    AuthInterceptor の path 判定 + TokenAuthenticator の path 判定の二重ガードで防ぐ。
        val refreshOnlyApi = ApiClientFactory.create(
            baseUrl = baseUrl,
            additionalInterceptors = listOf(AuthInterceptor(tokenStore)),
            authenticator = null,
        )
        val refreshOnlyUserRepo = UserRepositoryImpl(refreshOnlyApi)
        authRepository = AuthRepositoryImpl(
            api = refreshOnlyApi,
            tokenStore = tokenStore,
            userRepository = refreshOnlyUserRepo,
            clock = fixedClock,
        )

        // 2) 業務 API 用クライアント: AuthInterceptor + TokenAuthenticator を組み込んだ本番経路
        api = ApiClientFactory.create(
            baseUrl = baseUrl,
            additionalInterceptors = listOf(AuthInterceptor(tokenStore)),
            authenticator = TokenAuthenticator(authRepository, tokenStore),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun successTokenBody(
        accessToken: String = "new-AT",
        refreshToken: String = "new-RT",
        expiresIn: Long = 900,
    ): String = """
        {"access_token":"$accessToken","refresh_token":"$refreshToken","token_type":"Bearer","expires_in":$expiresIn}
    """.trimIndent()

    private fun unauthorizedBody(): String = """
        {"error":{"code":"UNAUTHORIZED","message":"unauthorized","category":"auth","action":"login"}}
    """.trimIndent()

    private fun invalidRefreshBody(): String = """
        {"error":{"code":"INVALID_REFRESH_TOKEN","message":"refresh expired","category":"auth","action":"login"}}
    """.trimIndent()

    /**
     * 任意のパス → 応答キューを持つ Dispatcher（MockWebServer はデフォルトで FIFO だが、
     * 並行テストや path-別の応答を制御したい場面では Dispatcher を使う方が決定論的）。
     */
    private class PathDispatcher(
        private val responses: MutableMap<String, ArrayDeque<MockResponse>>,
        val recordedPaths: MutableList<String> = mutableListOf(),
    ) : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.path?.substringBefore('?').orEmpty()
            synchronized(recordedPaths) {
                recordedPaths.add(path)
            }
            val queue = responses[path]
                ?: return MockResponse().setResponseCode(404).setBody("no enqueued response for $path")
            return synchronized(queue) {
                if (queue.isEmpty()) {
                    MockResponse().setResponseCode(404).setBody("queue empty for $path")
                } else {
                    queue.removeFirst()
                }
            }
        }
    }

    // ===== Requirement 1: 透過的なトークンリフレッシュと再試行 =====

    /** Req 1.1 / 1.2 / NFR 2.1: 401 → refresh → 新 access で元リクエストを 1 回再試行し成功応答を返す */
    @Test
    fun `Req 1-1 transparent refresh and retry returns success response to caller`() = runTest {
        // Arrange: TokenStore に既存トークンを保存
        tokenStore.save(TokenSet("old-AT", "old-RT", 0L))
        val responses = mutableMapOf(
            "/auth/me" to ArrayDeque(
                listOf(
                    MockResponse().setResponseCode(401).setBody(unauthorizedBody()),
                    MockResponse().setResponseCode(200).setBody("""{"id":"u-1","email":"alice@example.com"}"""),
                ),
            ),
            "/api/auth/refresh" to ArrayDeque(
                listOf(
                    MockResponse().setResponseCode(200).setBody(
                        successTokenBody(accessToken = "rotated-AT", refreshToken = "rotated-RT"),
                    ),
                ),
            ),
        )
        val dispatcher = PathDispatcher(responses)
        server.dispatcher = dispatcher

        // Act
        val user = api.getCurrentUser()

        // Assert
        assertEquals("u-1", user.id)
        // path 順序: /auth/me (401) → /api/auth/refresh (200) → /auth/me (200)
        assertEquals(
            listOf("/auth/me", "/api/auth/refresh", "/auth/me"),
            dispatcher.recordedPaths,
        )
        // 再試行時の Authorization は新トークン（Req 1.1）
        val saved = tokenStore.read()!!
        assertEquals("rotated-AT", saved.accessToken)
    }

    /**
     * Req 1.2: 元リクエスト → 401 → refresh 成功 → 元リクエスト 200 のシーケンスで、
     * 呼び出し元には 200 応答（success）が返り、401 / refresh 過程は露出しない。
     */
    @Test
    fun `Req 1-2 caller receives the successful response transparently after refresh`() = runTest {
        // Arrange
        tokenStore.save(TokenSet("old-AT", "old-RT", 0L))
        server.dispatcher = PathDispatcher(
            mutableMapOf(
                "/auth/me" to ArrayDeque(
                    listOf(
                        MockResponse().setResponseCode(401).setBody(unauthorizedBody()),
                        MockResponse().setResponseCode(200).setBody("""{"id":"abc","email":"x@y"}"""),
                    ),
                ),
                "/api/auth/refresh" to ArrayDeque(
                    listOf(MockResponse().setResponseCode(200).setBody(successTokenBody())),
                ),
            ),
        )

        // Act: 例外なく値が返る
        val user = api.getCurrentUser()

        // Assert
        assertEquals("abc", user.id)
    }

    /**
     * Req 1.3: 再試行したリクエストが 401 以外のエラー（404 等）を返した場合、
     * その応答はリフレッシュ前と同じ形式で（FeedmanException として）呼び出し元に伝搬する。
     */
    @Test
    fun `Req 1-3 caller receives non-401 error from retried request unchanged`() = runTest {
        // Arrange
        tokenStore.save(TokenSet("old-AT", "old-RT", 0L))
        val errorBody = """
            {"error":{"code":"NOT_FOUND","message":"not found","category":"client","action":"retry"}}
        """.trimIndent()
        server.dispatcher = PathDispatcher(
            mutableMapOf(
                "/auth/me" to ArrayDeque(
                    listOf(
                        MockResponse().setResponseCode(401).setBody(unauthorizedBody()),
                        MockResponse().setResponseCode(404).setBody(errorBody),
                    ),
                ),
                "/api/auth/refresh" to ArrayDeque(
                    listOf(MockResponse().setResponseCode(200).setBody(successTokenBody())),
                ),
            ),
        )

        // Act / Assert
        try {
            api.getCurrentUser()
            throw AssertionError("expected FeedmanException")
        } catch (e: FeedmanException) {
            assertEquals("NOT_FOUND", e.code)
            assertEquals(404, e.httpStatus)
        }
    }

    /**
     * Req 1.4 / NFR 1.1 / NFR 1.2: 1 つの元リクエストに対する自動 refresh + 再試行は 1 回限り。
     * 元リクエスト 401 → refresh 200 → 元リクエスト再試行 401 → これ以上の refresh をしない（401 伝播）。
     */
    @Test
    fun `Req 1-4 NFR 1-1 NFR 1-2 retry is limited to once per request even if retry returns 401`() = runTest {
        // Arrange
        tokenStore.save(TokenSet("old-AT", "old-RT", 0L))
        val dispatcher = PathDispatcher(
            mutableMapOf(
                "/auth/me" to ArrayDeque(
                    listOf(
                        MockResponse().setResponseCode(401).setBody(unauthorizedBody()),
                        MockResponse().setResponseCode(401).setBody(unauthorizedBody()),
                    ),
                ),
                "/api/auth/refresh" to ArrayDeque(
                    listOf(MockResponse().setResponseCode(200).setBody(successTokenBody())),
                ),
            ),
        )
        server.dispatcher = dispatcher

        // Act / Assert
        try {
            api.getCurrentUser()
            throw AssertionError("expected FeedmanException for second 401")
        } catch (e: FeedmanException) {
            assertEquals(401, e.httpStatus)
        }
        // refresh は 1 回しか呼ばれない（無限ループ防止 / NFR 1.1）
        val refreshCount = dispatcher.recordedPaths.count { it == "/api/auth/refresh" }
        assertEquals(1, refreshCount)
        // 元リクエストの再試行も 1 回限り（合計 2 回 = 初回 + 1 回再試行 / NFR 2.1）
        val meCount = dispatcher.recordedPaths.count { it == "/auth/me" }
        assertEquals(2, meCount)
    }

    // ===== Requirement 2: リフレッシュ不可時のセッション失効 =====

    /** Req 2.1: refresh token 未保存時に 401 → refresh も再試行も行わず、401 を呼び出し元へ伝搬 */
    @Test
    fun `Req 2-1 no refresh token in store skips refresh and propagates 401`() = runTest {
        // Arrange: TokenStore は empty
        assertNull(tokenStore.read())
        val dispatcher = PathDispatcher(
            mutableMapOf(
                "/auth/me" to ArrayDeque(
                    listOf(MockResponse().setResponseCode(401).setBody(unauthorizedBody())),
                ),
            ),
        )
        server.dispatcher = dispatcher

        // Act / Assert
        try {
            api.getCurrentUser()
            throw AssertionError("expected FeedmanException 401")
        } catch (e: FeedmanException) {
            assertEquals(401, e.httpStatus)
        }
        // refresh は呼ばれない
        assertEquals(0, dispatcher.recordedPaths.count { it == "/api/auth/refresh" })
        // 元リクエストは 1 回のみ（再試行なし）
        assertEquals(1, dispatcher.recordedPaths.count { it == "/auth/me" })
    }

    /**
     * Req 2.2 / 2.3 / 2.4: refresh が 401 を返した場合、AuthRepository が TokenStore を消去し
     * observeIsAuthenticated=false に遷移する。authenticator は再試行をせず 401 を呼び出し元へ伝搬する。
     */
    @Test
    fun `Req 2-2 Req 2-3 Req 2-4 refresh 401 clears tokens flips session false and propagates 401`() = runTest {
        // Arrange
        tokenStore.save(TokenSet("old-AT", "old-RT", 0L))
        authRepository.refreshAuthenticatedState()
        assertEquals(true, authRepository.observeIsAuthenticated().value)
        server.dispatcher = PathDispatcher(
            mutableMapOf(
                "/auth/me" to ArrayDeque(
                    listOf(MockResponse().setResponseCode(401).setBody(unauthorizedBody())),
                ),
                "/api/auth/refresh" to ArrayDeque(
                    listOf(MockResponse().setResponseCode(401).setBody(invalidRefreshBody())),
                ),
            ),
        )

        // Act / Assert
        try {
            api.getCurrentUser()
            throw AssertionError("expected FeedmanException 401")
        } catch (e: FeedmanException) {
            assertEquals(401, e.httpStatus)
        }
        // Req 2.2: TokenStore は AuthRepository.refresh の AuthRequired 経路で消去される
        assertNull(tokenStore.read())
        // Req 2.3: 観測可能な状態は false
        assertEquals(false, authRepository.observeIsAuthenticated().value)
    }

    /**
     * 認証エンドポイント自身（/api/auth/refresh）への 401 は TokenAuthenticator の対象外。
     * （AuthRepository.refresh() からの呼び出しで 401 が返っても、authenticator が再帰的に
     *  refresh を試みると無限ループになるため）
     */
    @Test
    fun `auth refresh endpoint is exempted from authenticator retry path`() = runBlocking {
        // Arrange: 直接 refresh API を api 経由（authenticator 入り）で叩く
        tokenStore.save(TokenSet("AT", "old-RT", 0L))
        val dispatcher = PathDispatcher(
            mutableMapOf(
                "/api/auth/refresh" to ArrayDeque(
                    listOf(MockResponse().setResponseCode(401).setBody(invalidRefreshBody())),
                ),
            ),
        )
        server.dispatcher = dispatcher

        // Act
        try {
            api.refreshAuthToken(RefreshTokenRequest(refreshToken = "old-RT"))
            throw AssertionError("expected FeedmanException 401")
        } catch (e: FeedmanException) {
            assertEquals(401, e.httpStatus)
        }
        // refresh エンドポイントは 1 回しか呼ばれない（authenticator は対象外）
        assertEquals(1, dispatcher.recordedPaths.count { it == "/api/auth/refresh" })
    }

    /**
     * 認証エンドポイント自身（/api/auth/token）への 401 も TokenAuthenticator の対象外。
     */
    @Test
    fun `auth token endpoint is exempted from authenticator retry path`() = runBlocking {
        // Arrange
        tokenStore.save(TokenSet("AT", "RT", 0L))
        val dispatcher = PathDispatcher(
            mutableMapOf(
                "/api/auth/token" to ArrayDeque(
                    listOf(MockResponse().setResponseCode(401).setBody(unauthorizedBody())),
                ),
            ),
        )
        server.dispatcher = dispatcher

        // Act
        try {
            api.exchangeAuthToken(TokenExchangeRequest(authCode = "c", codeVerifier = "v"))
            throw AssertionError("expected FeedmanException")
        } catch (e: FeedmanException) {
            assertEquals(401, e.httpStatus)
        }
        assertEquals(1, dispatcher.recordedPaths.count { it == "/api/auth/token" })
    }

    // ===== Requirement 3: 並行 401 単一飛行 =====

    /**
     * Req 3.1 / 3.2 / 3.4: 並行する複数の業務 API が 401 を受信したとき、refresh は 1 回に集約され、
     * 全件が新トークンで再試行されて成功応答を受け取る。
     */
    @Test
    fun `Req 3-1 Req 3-2 Req 3-4 concurrent 401s share single refresh and all retry once`() = runTest {
        // Arrange
        tokenStore.save(TokenSet("old-AT", "old-RT", 0L))

        // 4 並行リクエスト: それぞれ 401 → 成功 を順に返す。これを path 別 FIFO キューで実現。
        val parallelism = 4
        val responses = mutableMapOf(
            "/auth/me" to ArrayDeque(
                buildList {
                    repeat(parallelism) {
                        add(MockResponse().setResponseCode(401).setBody(unauthorizedBody()))
                    }
                    repeat(parallelism) {
                        add(
                            MockResponse()
                                .setResponseCode(200)
                                .setBody("""{"id":"u-1","email":"a@b"}"""),
                        )
                    }
                },
            ),
            "/api/auth/refresh" to ArrayDeque(
                listOf(
                    MockResponse()
                        .setResponseCode(200)
                        .setBody(successTokenBody(accessToken = "shared-AT", refreshToken = "shared-RT"))
                        // 並行性を確実にするため少し遅延させる
                        .setBodyDelay(200, TimeUnit.MILLISECONDS),
                ),
            ),
        )
        val dispatcher = PathDispatcher(responses)
        server.dispatcher = dispatcher

        // Act: 並行に getCurrentUser を呼ぶ
        val users = coroutineScope {
            val deferreds = List(parallelism) {
                async { api.getCurrentUser() }
            }
            deferreds.awaitAll()
        }

        // Assert
        // 1) 全件成功
        users.forEach { assertEquals("u-1", it.id) }
        // 2) refresh は 1 回のみ
        val refreshCount = dispatcher.recordedPaths.count { it == "/api/auth/refresh" }
        assertEquals("refresh should be coalesced to 1 call", 1, refreshCount)
        // 3) /auth/me は 401 用 + 200 用 = parallelism * 2 件
        val meCount = dispatcher.recordedPaths.count { it == "/auth/me" }
        assertEquals(parallelism * 2, meCount)
        // 4) TokenStore は rotation 結果
        val saved = tokenStore.read()
        assertNotNull(saved)
        assertEquals("shared-AT", saved!!.accessToken)
    }

    /**
     * Req 1.4 補強: priorResponse による retry-count 判定が機能している境界ケース。
     * 1 回目: 401 → refresh 成功 → 再試行 401 → ここで打ち止め（refresh 1 回のみ）
     */
    @Test
    fun `responseCount halts retry beyond 1 even with chained priorResponse`() = runTest {
        // Arrange
        tokenStore.save(TokenSet("old-AT", "old-RT", 0L))
        val dispatcher = PathDispatcher(
            mutableMapOf(
                "/auth/me" to ArrayDeque(
                    // 401 を 3 回 enqueue。authenticator が誤って複数回再試行すれば 3 回目を消費する。
                    List(3) { MockResponse().setResponseCode(401).setBody(unauthorizedBody()) }
                        .toMutableList(),
                ),
                "/api/auth/refresh" to ArrayDeque(
                    listOf(
                        MockResponse().setResponseCode(200).setBody(successTokenBody()),
                        MockResponse().setResponseCode(200).setBody(successTokenBody()),
                    ),
                ),
            ),
        )
        server.dispatcher = dispatcher

        // Act / Assert
        try {
            api.getCurrentUser()
            throw AssertionError("expected 401")
        } catch (e: FeedmanException) {
            assertEquals(401, e.httpStatus)
        }
        // /auth/me は 2 回のみ（初回 + 再試行 1 回）。3 回目は消費されない。
        assertEquals(2, dispatcher.recordedPaths.count { it == "/auth/me" })
        // refresh も 1 回のみ
        assertEquals(1, dispatcher.recordedPaths.count { it == "/api/auth/refresh" })
    }
}
