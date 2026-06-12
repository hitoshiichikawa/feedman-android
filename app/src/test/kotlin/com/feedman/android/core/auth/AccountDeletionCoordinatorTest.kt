package com.feedman.android.core.auth

import com.feedman.android.core.auth.fake.InMemoryTokenStore
import com.feedman.android.core.data.CrossFeedRepositoryImpl
import com.feedman.android.core.data.ItemDetailRepository
import com.feedman.android.core.data.ItemStateStore
import com.feedman.android.core.data.SubscriptionRepositoryImpl
import com.feedman.android.core.data.UserRepositoryImpl
import com.feedman.android.core.model.ItemDetail
import com.feedman.android.core.network.ApiClientFactory
import com.feedman.android.core.network.FeedmanApi
import com.feedman.android.core.network.FeedmanException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * [AccountDeletionCoordinator] の単体テスト（Issue #51 requirements.md Req 4 / Req 5 / NFR 2）。
 *
 * MockWebServer + 実 UserRepositoryImpl + 実 TokenStore + 実
 * SubscriptionRepositoryImpl + 実 CrossFeedRepositoryImpl + 実 ItemStateStore で組み立て、
 * observable な結果として「DELETE /api/users/me が 1 回呼ばれる」「成功時に TokenStore と
 * ユーザースコープキャッシュが空になる」「失敗時にローカル状態が温存される」を検証する
 * （CLAUDE.md テスト規約: Retrofit / OkHttp / TokenStore をモックしない）。
 *
 * 対応 AC:
 * - Req 2.6: 確定操作で DELETE /api/users/me を 1 回送信
 * - Req 4.1: 成功時に TokenStore（access / refresh）が空になる
 * - Req 4.2: 成功時に ItemStateStore / SubscriptionRepository / CrossFeedRepository が reset される
 * - Req 4.3: 成功時に observeIsAuthenticated が false に遷移する（SessionState の LoggedOut 経路）
 * - Req 5.1: サーバーエラー時に TokenStore は維持される
 * - Req 5.2: サーバーエラー時に observeIsAuthenticated は true のまま（SessionState LoggedIn のまま）
 * - Req 5.3: ネットワーク失敗時に TokenStore / SessionState が維持される
 * - Req 5.4: 失敗時に message を含む Failure が返される（UI 表示用）
 * - Req 5.5: 失敗後の再試行が可能（perform を再度呼んで成功できる）
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountDeletionCoordinatorTest {

    private lateinit var server: MockWebServer
    private lateinit var baseUrl: String
    private lateinit var tokenStore: InMemoryTokenStore
    private lateinit var api: FeedmanApi
    private lateinit var authRepository: AuthRepositoryImpl
    private lateinit var userRepository: UserRepositoryImpl
    private lateinit var itemStateStore: ItemStateStore
    private lateinit var subscriptionRepository: SubscriptionRepositoryImpl
    private lateinit var crossFeedRepository: CrossFeedRepositoryImpl
    private lateinit var coordinator: AccountDeletionCoordinatorImpl
    private lateinit var appScope: CoroutineScope

    private val fixedClock: Clock = Clock.fixed(Instant.ofEpochMilli(1_000_000L), ZoneOffset.UTC)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        baseUrl = server.url("/").toString()
        tokenStore = InMemoryTokenStore()
        api = ApiClientFactory.create(
            baseUrl = baseUrl,
            additionalInterceptors = listOf(AuthInterceptor(tokenStore)),
        )
        userRepository = UserRepositoryImpl(api)
        authRepository = AuthRepositoryImpl(
            api = api,
            tokenStore = tokenStore,
            userRepository = userRepository,
            clock = fixedClock,
        )
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        itemStateStore = ItemStateStore(
            repository = NoOpItemDetailRepository,
            scope = appScope,
        )
        subscriptionRepository = SubscriptionRepositoryImpl(api)
        crossFeedRepository = CrossFeedRepositoryImpl(api)

        coordinator = AccountDeletionCoordinatorImpl(
            userRepository = userRepository,
            tokenStore = tokenStore,
            authRepository = authRepository,
            itemStateStore = itemStateStore,
            subscriptionRepository = subscriptionRepository,
            crossFeedRepository = crossFeedRepository,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
        appScope.cancel()
    }

    // ── 事前準備 ────────────────────────────────────────────────────

    /** 保存済みトークンと「前ユーザーの状態」を仕込む。 */
    private suspend fun seedAuthenticatedSession() {
        tokenStore.save(
            TokenSet(
                accessToken = "AT",
                refreshToken = "RT",
                accessTokenExpiresAtEpochMillis = Long.MAX_VALUE,
            ),
        )
        authRepository.refreshAuthenticatedState()
        itemStateStore.setRead(itemId = "i1", isRead = true, baselineRead = false)
    }

    // ── 正常系（Req 2.6 / 4.1 / 4.2 / 4.3）───────────────────────────

    @Test
    fun `Req 2_6 perform は DELETE api users me を 1 回呼ぶ`() = runBlocking {
        // Arrange
        seedAuthenticatedSession()
        server.enqueue(MockResponse().setResponseCode(204))

        // Act
        val result = coordinator.perform()

        // Assert: SPEC §5.7 の DELETE /api/users/me を 1 回送信（Req 2.6）
        val recorded = server.takeRequest()
        assertEquals("DELETE", recorded.method)
        assertEquals("/api/users/me", recorded.requestUrl?.encodedPath)
        assertEquals(DeletionResult.Success, result)
    }

    @Test
    fun `Req 4_1 成功で TokenStore は空になる`() = runBlocking {
        // Arrange
        seedAuthenticatedSession()
        server.enqueue(MockResponse().setResponseCode(204))
        // 事前: TokenStore に書き込まれている
        assertNotNull(tokenStore.read())

        // Act
        coordinator.perform()

        // Assert: Req 4.1 — access / refresh の両方が消去される
        assertNull("成功時 TokenStore は空", tokenStore.read())
    }

    @Test
    fun `Req 4_2 成功で ItemStateStore overlay が空になる`() = runBlocking {
        // Arrange
        seedAuthenticatedSession()
        server.enqueue(MockResponse().setResponseCode(204))
        assertEquals(true, itemStateStore.overlays.first()["i1"]?.isRead)

        // Act
        coordinator.perform()

        // Assert: Req 4.2 — ユーザースコープキャッシュがリセットされる
        assertTrue(
            "ItemStateStore.overlays は coordinator 経由でリセットされている",
            itemStateStore.overlays.first().isEmpty(),
        )
    }

    @Test
    fun `Req 4_3 成功で observeIsAuthenticated が false に遷移する`() = runBlocking {
        // Arrange
        seedAuthenticatedSession()
        assertEquals(true, authRepository.observeIsAuthenticated().value)
        server.enqueue(MockResponse().setResponseCode(204))

        // Act
        coordinator.perform()

        // Assert: Req 4.3 / 4.4 — SessionState 遷移経路（observeIsAuthenticated → LoggedOut）が成立
        assertEquals(false, authRepository.observeIsAuthenticated().value)
    }

    @Test
    fun `Req 2_6 perform は revoke を呼ばない_アカウント自体が消えるため不要`() = runBlocking {
        // Arrange
        seedAuthenticatedSession()
        server.enqueue(MockResponse().setResponseCode(204))

        // Act
        coordinator.perform()

        // Assert: revoke エンドポイントへの送信は無いこと（リクエスト総数 = DELETE 1 件のみ）
        assertEquals(1, server.requestCount)
        // 念のため最初のリクエストが revoke ではないことを確認
        // （上の Req 2_6 テストで DELETE /api/users/me を確認済みだが、本テストでは
        //   revoke の不在をリクエスト総数で別観点として明示する）
    }

    // ── 失敗系（Req 5.1 / 5.2 / 5.3 / 5.4 / 5.5）─────────────────────

    @Test
    fun `Req 5_1 5_2 サーバーエラーで TokenStore は維持される_観測 isAuthenticated は true のまま`() =
        runBlocking {
            // Arrange: SPEC §4.3 のエラーボディ
            seedAuthenticatedSession()
            val errorBody = """
                {"error":{"code":"INTERNAL","message":"退会処理に失敗しました","category":"server","action":"retry"}}
            """.trimIndent()
            server.enqueue(MockResponse().setResponseCode(500).setBody(errorBody))

            // Act
            val result = coordinator.perform()

            // Assert: Req 5.1 — TokenStore 温存
            assertNotNull("サーバーエラーでも TokenStore は維持", tokenStore.read())
            // Req 5.2 — SessionState は LoggedIn のまま（observeIsAuthenticated = true）
            assertEquals(true, authRepository.observeIsAuthenticated().value)
            // Req 5.4 — Failure に message を含む（サーバー由来文言が優先）
            assertTrue(result is DeletionResult.Failure)
            assertEquals(
                "退会処理に失敗しました",
                (result as DeletionResult.Failure).message,
            )
        }

    @Test
    fun `Req 5_1 サーバーエラーでも ItemStateStore overlay は維持される`() = runBlocking {
        // Arrange
        seedAuthenticatedSession()
        val errorBody = """
            {"error":{"code":"INTERNAL","message":"server","category":"server","action":"retry"}}
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(500).setBody(errorBody))
        assertEquals(true, itemStateStore.overlays.first()["i1"]?.isRead)

        // Act
        coordinator.perform()

        // Assert: Req 5.1 — ユーザースコープキャッシュも温存
        assertFalse(
            "サーバーエラー時 ItemStateStore は維持される",
            itemStateStore.overlays.first().isEmpty(),
        )
    }

    @Test
    fun `Req 5_3 ネットワーク失敗で TokenStore と SessionState は維持される`() = runBlocking {
        // Arrange: 接続切断
        seedAuthenticatedSession()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        // Act
        val result = coordinator.perform()

        // Assert: Req 5.3 — TokenStore 温存 / observeIsAuthenticated は true のまま
        assertNotNull("ネットワーク失敗でも TokenStore は維持", tokenStore.read())
        assertEquals(true, authRepository.observeIsAuthenticated().value)
        // Req 5.4 — Failure に非空 message
        assertTrue(result is DeletionResult.Failure)
        val message = (result as DeletionResult.Failure).message
        assertTrue("ネットワーク失敗の message は非空", message.isNotBlank())
    }

    @Test
    fun `Req 5_4 errorMessage 空のサーバーエラーは code 別フォールバック文言を採用する`() = runBlocking {
        // Arrange: errorMessage が空 / category が server（カスタムエラー扱い）
        seedAuthenticatedSession()
        val errorBody = """
            {"error":{"code":"INTERNAL","message":"","category":"server","action":"retry"}}
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(500).setBody(errorBody))

        // Act
        val result = coordinator.perform()

        // Assert: Req 5.4 — 空文字でなくフォールバック文言が入る
        assertTrue(result is DeletionResult.Failure)
        val message = (result as DeletionResult.Failure).message
        assertEquals(FeedmanException.FALLBACK_UNKNOWN_MESSAGE, message)
    }

    @Test
    fun `Req 5_5 失敗後に再 perform で成功する_リエントランシ`() = runBlocking {
        // Arrange: 1 回目はサーバーエラー、2 回目は成功
        seedAuthenticatedSession()
        val errorBody = """
            {"error":{"code":"INTERNAL","message":"一時的エラー","category":"server","action":"retry"}}
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(500).setBody(errorBody))
        server.enqueue(MockResponse().setResponseCode(204))

        // Act: 失敗 → 再試行
        val first = coordinator.perform()
        assertTrue(first is DeletionResult.Failure)
        // 失敗後、ローカル状態は維持
        assertNotNull(tokenStore.read())
        val second = coordinator.perform()

        // Assert: Req 5.5 — 再試行で成功し、ローカル状態が消去される
        assertEquals(DeletionResult.Success, second)
        assertNull(tokenStore.read())
        assertEquals(false, authRepository.observeIsAuthenticated().value)
    }

    // ── 共有テストダブル ───────────────────────────────────────────

    private object NoOpItemDetailRepository : ItemDetailRepository {
        override suspend fun getItem(itemId: String): ItemDetail =
            error("getItem must not be called in this test")

        override suspend fun updateState(itemId: String, isRead: Boolean?, isStarred: Boolean?) {
            // ItemStateStore.setRead 内で呼ばれるが本テストでは結果に依存しない。
        }
    }
}
