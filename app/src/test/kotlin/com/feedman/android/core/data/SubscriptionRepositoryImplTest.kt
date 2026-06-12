package com.feedman.android.core.data

import app.cash.turbine.test
import com.feedman.android.core.model.FixtureLoader
import com.feedman.android.core.network.ApiClientFactory
import com.feedman.android.core.network.FeedmanException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
 * [SubscriptionRepositoryImpl] の単体テスト（Issue #39 / requirements.md AC 1.x, 2.x, 4.x, NFR 2.1）。
 *
 * `FeedmanApi` をモックせず、`MockWebServer` で実 HTTP レスポンスを返す（CLAUDE.md
 * テスト規約: 「repository / APIClient のテストは MockWebServer で実レスポンス JSON を返す」）。
 *
 * フィクスチャは `app/src/test/resources/fixtures/subscription_active.json` /
 * `subscription_error.json` を流用し、配列にラップしてレスポンスを構築する。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionRepositoryImplTest {

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

    private fun newRepository(): SubscriptionRepositoryImpl {
        val api = ApiClientFactory.create(baseUrl = baseUrl)
        return SubscriptionRepositoryImpl(api)
    }

    // ===== Requirement 1: 実 API による購読一覧の取得 =====

    @Test
    fun `Req 1_1 refresh で api subscriptions エンドポイントを GET する`() = runTest {
        // Arrange
        val active = FixtureLoader.load("subscription_active.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody("[$active]"))
        val repo = newRepository()

        // Act
        repo.refresh()

        // Assert
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/api/subscriptions", recorded.requestUrl?.encodedPath)
    }

    @Test
    fun `Req 1_2 1_3 200 応答を Subscription 配列として decode し observe へ流す`() = runTest {
        // Arrange
        val active = FixtureLoader.load("subscription_active.json")
        val error = FixtureLoader.load("subscription_error.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody("[$active,$error]"))
        val repo = newRepository()

        // Act
        repo.refresh()

        // Assert
        val items = repo.observeSubscriptions().first()
        assertEquals(2, items.size)
        // Req 1.3: 必要なフィールドが decode されている
        assertEquals("01HGY8K9ZQ4N7TXVY1F8M9R3FE", items[0].feedId)
        assertEquals("Feedman Dev Blog", items[0].feedTitle)
        assertTrue(items[0].faviconUrl?.startsWith("data:") == true)
        assertEquals(12, items[0].unreadCount)
        assertEquals("active", items[0].feedStatus)
        // 2 件目（error）
        assertEquals("error", items[1].feedStatus)
        assertEquals("HTTP 503 Service Unavailable", items[1].errorMessage)
    }

    @Test
    fun `Req 1_4 サーバーが返した順序を変更せずそのまま流す`() = runTest {
        // Arrange: わざと「error → active」の順で返す
        val active = FixtureLoader.load("subscription_active.json")
        val error = FixtureLoader.load("subscription_error.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody("[$error,$active]"))
        val repo = newRepository()

        // Act
        repo.refresh()

        // Assert: 取り出し順がサーバーレスポンス順と一致する
        val items = repo.observeSubscriptions().first()
        assertEquals(listOf("error", "active"), items.map { it.feedStatus })
    }

    @Test
    fun `Req 1_5 空配列のとき空のフィードリストを流す`() = runTest {
        // Arrange
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        val repo = newRepository()

        // Act
        repo.refresh()

        // Assert
        val items = repo.observeSubscriptions().first()
        assertTrue(items.isEmpty())
        // 取得状態は Success に遷移する
        assertEquals(SubscriptionLoadState.Success, repo.observeLoadState().first())
    }

    // ===== Requirement 2: 取得失敗時のエラー状態通知 =====

    @Test
    fun `Req 2_1 2_6 非 2xx で SPEC エラー応答が来たら Error 状態で message と code を通知する`() = runTest {
        // Arrange: SPEC §4.3 統一エラー fixture
        val errorBody = FixtureLoader.load("error_invalid_request.json")
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody(errorBody),
        )
        val repo = newRepository()

        // Act
        repo.refresh()

        // Assert: Req 2.1: Error 状態として観測者へ通知
        val state = repo.observeLoadState().first()
        assertTrue("state should be Error: $state", state is SubscriptionLoadState.Error)
        val error = state as SubscriptionLoadState.Error
        // Req 2.6: SPEC §4.3 統一エラーの message をユーザー向け文言として保持する
        assertEquals("リクエストパラメータが不正です。", error.message)
        assertEquals("INVALID_REQUEST", error.code)
    }

    @Test
    fun `Req 2_1 ネットワーク失敗時に Error 状態で通知する code は NETWORK_ERROR`() = runTest {
        // Arrange: 接続を即時切断
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val repo = newRepository()

        // Act
        repo.refresh()

        // Assert
        val state = repo.observeLoadState().first()
        assertTrue("state should be Error: $state", state is SubscriptionLoadState.Error)
        val error = state as SubscriptionLoadState.Error
        assertEquals(FeedmanException.CODE_NETWORK_ERROR, error.code)
        // message は空にしない（フォールバック文言を埋める）
        assertTrue("message should not be empty", error.message.isNotBlank())
    }

    @Test
    fun `Req 2_4 再試行 refresh で 2 回目の取得が走り Success に回復する`() = runTest {
        // Arrange: 1 回目は失敗、2 回目は成功
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val active = FixtureLoader.load("subscription_active.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody("[$active]"))
        val repo = newRepository()

        // Act-1: 失敗
        repo.refresh()
        assertTrue(repo.observeLoadState().first() is SubscriptionLoadState.Error)

        // Act-2: 再試行
        repo.refresh()

        // Assert: Success に回復、リストが反映
        assertEquals(SubscriptionLoadState.Success, repo.observeLoadState().first())
        val items = repo.observeSubscriptions().first()
        assertEquals(1, items.size)
        assertEquals("active", items[0].feedStatus)
    }

    // ===== Requirement 4: 認証エラー時の挙動 =====

    @Test
    fun `Req 4_3 401 が継続したら識別可能な Error 状態として通知する`() = runTest {
        // Arrange: 共通認証層が再認証に失敗した結果として 401 が伝搬してくる状況を模擬する。
        //         （本テストでは authenticator を装着していないため、401 が直接非 2xx として透過する）
        val errorBody = """
            {
              "error": {
                "code": "UNAUTHORIZED",
                "message": "ログインが必要です。",
                "category": "client",
                "action": "login_required"
              }
            }
        """.trimIndent()
        server.enqueue(
            MockResponse().setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody(errorBody),
        )
        val repo = newRepository()

        // Act
        repo.refresh()

        // Assert
        val state = repo.observeLoadState().first()
        assertTrue("state should be Error: $state", state is SubscriptionLoadState.Error)
        val error = state as SubscriptionLoadState.Error
        // Req 4.3: 識別可能な認証エラー状態として code が UNAUTHORIZED で観測される
        assertEquals("UNAUTHORIZED", error.code)
    }

    // ===== Loading 状態の遷移（Req 2.5 / NFR 2.1） =====

    @Test
    fun `Req 2_5 refresh 中は Loading 状態を観測者へ通知する`() = runTest {
        // Arrange
        val active = FixtureLoader.load("subscription_active.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody("[$active]"))
        val repo = newRepository()

        // Act + Assert: 初期 → Loading → Success の状態遷移を turbine で観測する
        repo.observeLoadState().test {
            assertEquals(SubscriptionLoadState.Idle, awaitItem())
            // refresh を起動すると Loading に遷移する
            repo.refresh()
            // 並びは Loading → Success だが、UnconfinedTestDispatcher により直列で全 emit が
            // 観測キューに積まれる
            val seen = mutableListOf<SubscriptionLoadState>()
            seen += awaitItem()
            seen += awaitItem()
            assertTrue(
                "should observe Loading then Success: $seen",
                seen == listOf(SubscriptionLoadState.Loading, SubscriptionLoadState.Success),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }
}
