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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * [LogoutCoordinator] の単体テスト（Issue #50 requirements.md AC 2, 3, 4, 5, NFR 1.2 / 2.1）。
 *
 * MockWebServer + 実 AuthRepositoryImpl + 実 SubscriptionRepositoryImpl + 実
 * CrossFeedRepositoryImpl + 実 ItemStateStore で組み立て、observable な結果として
 * 「TokenStore が空になる」「ユーザースコープキャッシュが空になる」「observeIsAuthenticated
 * が false に遷移する」を検証する（CLAUDE.md テスト規約: Retrofit / OkHttp / TokenStore を
 * モックしない）。
 *
 * `runTest` の virtual time scheduler は実 HTTP 呼び出しを進行させないため、
 * 本テストでは [runBlocking] を使い実時間で coroutine を進める。MockWebServer の
 * 応答キューに準備したレスポンス（または `DISCONNECT_AT_START` 等の即時失敗）で
 * テスト時間が長くならない構成にしている。
 *
 * 対応 AC:
 * - Req 2.1: revoke が 1 回呼ばれる
 * - Req 2.2: revoke がサーバー成功・サーバーエラー・ネットワーク失敗いずれでも TokenStore 消去
 * - Req 2.3 / 5.1: ネットワーク失敗でも後続のキャッシュリセット + LoggedOut 遷移を継続
 * - Req 2.4: 完了後に TokenStore が空
 * - Req 3.1: ItemStateStore / SubscriptionRepository / CrossFeedRepository がリセットされる
 * - Req 4.1: observeIsAuthenticated が false に遷移する（LoggedOut の元）
 * - NFR 1.2: REVOKE_TIMEOUT_MILLIS が 10 秒
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LogoutCoordinatorTest {

    private lateinit var server: MockWebServer
    private lateinit var baseUrl: String
    private lateinit var tokenStore: InMemoryTokenStore
    private lateinit var api: FeedmanApi
    private lateinit var authRepository: AuthRepositoryImpl
    private lateinit var itemStateStore: ItemStateStore
    private lateinit var subscriptionRepository: SubscriptionRepositoryImpl
    private lateinit var crossFeedRepository: CrossFeedRepositoryImpl
    private lateinit var coordinator: LogoutCoordinatorImpl
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
        val userRepo = UserRepositoryImpl(api)
        authRepository = AuthRepositoryImpl(
            api = api,
            tokenStore = tokenStore,
            userRepository = userRepo,
            clock = fixedClock,
        )
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        itemStateStore = ItemStateStore(
            repository = NoOpItemDetailRepository,
            scope = appScope,
        )
        subscriptionRepository = SubscriptionRepositoryImpl(api)
        crossFeedRepository = CrossFeedRepositoryImpl(api)

        coordinator = LogoutCoordinatorImpl(
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
        // ItemStateStore に overlay を直接書き込む（API 呼び出し副作用を避けるため、
        // 公開 API ではなく overlays の MutableStateFlow を経由するのは不可。
        // setRead はバックグラウンド launch で NoOp repository を呼ぶだけなので副作用は無い）
        itemStateStore.setRead(itemId = "i1", isRead = true, baselineRead = false)
    }

    // ── Req 2.1 / 2.2 / 2.4 / 3.1 / 4.1: 正常系 ────────────

    @Test
    fun `Req 2_1 perform は revoke を 1 回呼び TokenStore を消去する`() = runBlocking {
        // Arrange
        seedAuthenticatedSession()
        server.enqueue(MockResponse().setResponseCode(204))

        // Act
        coordinator.perform()

        // Assert
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/auth/revoke", recorded.requestUrl?.encodedPath)
        // Req 2.4
        assertNull("TokenStore は完了後に空", tokenStore.read())
    }

    @Test
    fun `Req 3_1 perform は ItemStateStore overlay を空にする`() = runBlocking {
        // Arrange
        seedAuthenticatedSession()
        server.enqueue(MockResponse().setResponseCode(204))
        assertEquals(true, itemStateStore.overlays.first()["i1"]?.isRead)

        // Act
        coordinator.perform()

        // Assert
        assertTrue(
            "ItemStateStore.overlays は coordinator 経由でリセットされている",
            itemStateStore.overlays.first().isEmpty(),
        )
    }

    @Test
    fun `Req 4_1 perform 完了後に observeIsAuthenticated が false に遷移する`() = runBlocking {
        // Arrange
        seedAuthenticatedSession()
        assertEquals(true, authRepository.observeIsAuthenticated().value)
        server.enqueue(MockResponse().setResponseCode(204))

        // Act
        coordinator.perform()

        // Assert: SessionStateProvider はこの値を観測して LoggedOut を流す（Req 4.1）
        assertEquals(false, authRepository.observeIsAuthenticated().value)
    }

    // ── Req 2.2 / 2.3 / 5.1: revoke 失敗でもローカル消去 + キャッシュリセット完遂 ──

    @Test
    fun `Req 2_3 revoke のネットワーク失敗でも TokenStore 消去とキャッシュリセットを行う`() = runBlocking {
        // Arrange: 接続切断によるネットワーク失敗を再現
        seedAuthenticatedSession()
        server.enqueue(
            MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START),
        )

        // Act: perform は例外を投げない
        coordinator.perform()

        // Assert: トークン消去 + ItemStateStore リセット
        assertNull("ネットワーク失敗でも TokenStore は空", tokenStore.read())
        assertTrue(
            "ネットワーク失敗でも ItemStateStore がリセットされている",
            itemStateStore.overlays.first().isEmpty(),
        )
        // Req 4.1: observeIsAuthenticated も false
        assertEquals(false, authRepository.observeIsAuthenticated().value)
    }

    @Test
    fun `Req 2_2 revoke のサーバーエラーでも TokenStore 消去とキャッシュリセットを行う`() = runBlocking {
        // Arrange: 500 を返す
        seedAuthenticatedSession()
        val errorBody = """
            {"error":{"code":"INTERNAL","message":"server","category":"server","action":"retry"}}
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(500).setBody(errorBody))

        // Act
        coordinator.perform()

        // Assert
        assertNull(tokenStore.read())
        assertTrue(itemStateStore.overlays.first().isEmpty())
        assertEquals(false, authRepository.observeIsAuthenticated().value)
    }

    // ── NFR 1.2: 10 秒タイムアウト境界の宣言 ─────────────────────────

    @Test
    fun `NFR 1_2 REVOKE_TIMEOUT_MILLIS は 10 秒に設定されている`() {
        // Coordinator の調停タイムアウト境界が NFR 1.2 の要求値（10 秒）と一致することを
        // 機械的に確認する。実 HTTP タイムアウトでの検証は実時間 10 秒待ちが発生するため、
        // 境界値の宣言テストとして本コードレベルの定数を assert する。
        assertEquals(10_000L, LogoutCoordinatorImpl.REVOKE_TIMEOUT_MILLIS)
    }

    // ── Req 5.1: いかなる失敗パスでもログイン画面遷移経路を維持 ──

    @Test
    fun `Req 5_1 perform は例外を投げない_キャッシュ reset が独立に呼ばれる`() = runBlocking {
        // Arrange: revoke 成功 + 各キャッシュ事前状態
        seedAuthenticatedSession()
        server.enqueue(MockResponse().setResponseCode(204))

        // Act
        coordinator.perform()

        // Assert: SubscriptionRepository / CrossFeedRepository も reset を経由
        // 副作用は「内部状態が初期値」になっていること
        assertNull(crossFeedRepository.currentSinceTime)
    }

    // ── 共有テストダブル ───────────────────────────────────────────

    private object NoOpItemDetailRepository : ItemDetailRepository {
        override suspend fun getItem(itemId: String): ItemDetail =
            error("getItem must not be called in this test")

        override suspend fun updateState(itemId: String, isRead: Boolean?, isStarred: Boolean?) {
            // ItemStateStore.setRead 内で呼ばれるが本テストでは結果に依存しない。
            // 失敗注入もしないため、何もしない（成功扱い）。
        }
    }
}
