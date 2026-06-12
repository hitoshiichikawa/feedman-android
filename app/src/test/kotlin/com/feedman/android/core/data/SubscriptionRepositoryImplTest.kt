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

    // ===== Issue #41: resume / observeFeed =====

    @Test
    fun `Issue41 Req 3_5 resume で api subscriptions id resume を POST する`() = runTest {
        // Arrange: refresh 用 + resume 用の 2 レスポンス
        val active = FixtureLoader.load("subscription_active.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody("[$active]"))
        server.enqueue(MockResponse().setResponseCode(200).setBody(active))
        val repo = newRepository()
        repo.refresh()
        server.takeRequest() // refresh 分の record を捨てる

        // Act
        repo.resume("01HGY8K9ZQ4N7TXVY1F8M9R3SU")

        // Assert
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals(
            "/api/subscriptions/01HGY8K9ZQ4N7TXVY1F8M9R3SU/resume",
            recorded.requestUrl?.encodedPath,
        )
    }

    @Test
    fun `Issue41 Req 3_7 resume 成功で観測中の Subscription が active に更新される`() = runTest {
        // Arrange: 1) refresh で error 状態の購読をリストに載せる
        val errorJson = FixtureLoader.load("subscription_error.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody("[$errorJson]"))
        val repo = newRepository()
        repo.refresh()
        val before = repo.observeSubscriptions().first().single()
        assertEquals("error", before.feedStatus)

        // 2) resume レスポンスとして active 状態を返す
        val activeJson = errorJson
            .replace("\"error\"", "\"active\"")
            .replace("\"HTTP 503 Service Unavailable\"", "null")
        server.enqueue(MockResponse().setResponseCode(200).setBody(activeJson))

        // Act
        val returned = repo.resume(before.id)

        // Assert: 戻り値 + 観測ストリーム双方が active に切り替わる
        assertEquals("active", returned.feedStatus)
        val afterList = repo.observeSubscriptions().first()
        assertEquals(1, afterList.size)
        assertEquals("active", afterList[0].feedStatus)
    }

    @Test
    fun `Issue41 Req 3_8 resume 失敗時は例外を伝搬し購読リストを変えない`() = runTest {
        // Arrange
        val errorJson = FixtureLoader.load("subscription_error.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody("[$errorJson]"))
        val repo = newRepository()
        repo.refresh()
        val before = repo.observeSubscriptions().first().single()

        // resume が 503 で失敗する
        val errorBody = """
            {
              "error": {
                "code": "UPSTREAM_ERROR",
                "message": "再開に失敗しました。"
              }
            }
        """.trimIndent()
        server.enqueue(
            MockResponse().setResponseCode(503)
                .setHeader("Content-Type", "application/json")
                .setBody(errorBody),
        )

        // Act / Assert: 例外が呼び出し元へ投げ返される
        var thrown: Throwable? = null
        try {
            repo.resume(before.id)
        } catch (e: Throwable) {
            thrown = e
        }
        assertTrue("expected FeedmanException, got $thrown", thrown is FeedmanException)
        // 購読リストは変わらない（error のまま）
        val after = repo.observeSubscriptions().first().single()
        assertEquals("error", after.feedStatus)
    }

    @Test
    fun `Issue41 Req 4_1 observeFeed で feedId に一致する Subscription を取り出せる`() = runTest {
        // Arrange: 複数フィードをロード
        val active = FixtureLoader.load("subscription_active.json")
        val errorJson = FixtureLoader.load("subscription_error.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody("[$active,$errorJson]"))
        val repo = newRepository()
        repo.refresh()

        // Act: active 側の feedId で観測
        val found = repo.observeFeed("01HGY8K9ZQ4N7TXVY1F8M9R3FE").first()

        // Assert
        assertEquals("Feedman Dev Blog", found?.feedTitle)
        assertEquals("active", found?.feedStatus)
    }

    @Test
    fun `Issue41 Req 4_3 observeFeed は未存在 feedId に対して null を流す`() = runTest {
        // Arrange
        val active = FixtureLoader.load("subscription_active.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody("[$active]"))
        val repo = newRepository()
        repo.refresh()

        // Act
        val notFound = repo.observeFeed("does-not-exist").first()

        // Assert
        assertEquals(null, notFound)
    }

    // ===== Issue #42: 手動フェッチ（fetch） =====

    @Test
    fun `Issue42 Req 1_1 fetch で api subscriptions id fetch を POST する`() = runTest {
        // Arrange: refresh 用 + fetch 用の 2 レスポンス
        val active = FixtureLoader.load("subscription_active.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody("[$active]"))
        server.enqueue(MockResponse().setResponseCode(200).setBody(active))
        val repo = newRepository()
        repo.refresh()
        server.takeRequest() // refresh 分の record を捨てる

        // Act
        repo.fetch("01HGY8K9ZQ4N7TXVY1F8M9R3SU")

        // Assert
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals(
            "/api/subscriptions/01HGY8K9ZQ4N7TXVY1F8M9R3SU/fetch",
            recorded.requestUrl?.encodedPath,
        )
    }

    @Test
    fun `Issue42 Req 2_3 fetch 成功で観測中の Subscription が unread count 更新を反映する`() = runTest {
        // Arrange: 1) refresh で unread_count=12 の active 状態をリストに載せる
        val active = FixtureLoader.load("subscription_active.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody("[$active]"))
        val repo = newRepository()
        repo.refresh()
        val before = repo.observeSubscriptions().first().single()
        assertEquals(12, before.unreadCount)

        // 2) fetch レスポンスとして unread_count を増やした active を返す
        val updatedJson = active.replace("\"unread_count\": 12", "\"unread_count\": 17")
        server.enqueue(MockResponse().setResponseCode(200).setBody(updatedJson))

        // Act
        val returned = repo.fetch(before.id)

        // Assert: 戻り値 + 観測ストリーム双方が unread_count = 17 に切り替わる
        assertEquals(17, returned.unreadCount)
        val afterList = repo.observeSubscriptions().first()
        assertEquals(1, afterList.size)
        assertEquals(17, afterList[0].unreadCount)
    }

    @Test
    fun `Issue42 Req 3_1 fetch がクールダウン応答時 FEED_COOLDOWN と retryAfterSeconds 付きで例外を投げる`() = runTest {
        // Arrange
        val active = FixtureLoader.load("subscription_active.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody("[$active]"))
        val repo = newRepository()
        repo.refresh()
        val before = repo.observeSubscriptions().first().single()

        // クールダウン応答（SPEC §4.3）
        val errorBody = """
            {
              "error": {
                "code": "FEED_COOLDOWN",
                "message": "クールダウン中です。",
                "category": "rate_limit",
                "action": "wait_and_retry",
                "details": { "retry_after_seconds": 30 }
              }
            }
        """.trimIndent()
        server.enqueue(
            MockResponse().setResponseCode(429)
                .setHeader("Content-Type", "application/json")
                .setBody(errorBody),
        )

        // Act / Assert
        var thrown: Throwable? = null
        try {
            repo.fetch(before.id)
        } catch (e: Throwable) {
            thrown = e
        }
        assertTrue("expected FeedmanException, got $thrown", thrown is FeedmanException)
        val fe = thrown as FeedmanException
        assertEquals("FEED_COOLDOWN", fe.code)
        assertEquals(30, fe.retryAfterSeconds)
        // 購読リストは変わらない（active のまま、unread_count 等が手元のキャッシュ値を維持）
        val after = repo.observeSubscriptions().first().single()
        assertEquals(before.unreadCount, after.unreadCount)
    }

    // ===== Issue #43: 購読設定更新（updateSettings） =====

    @Test
    fun `Issue43 Req 2_4 updateSettings で api subscriptions id settings を PUT する fetch_interval_minutes ボディ`() = runTest {
        // Arrange: refresh 用 + updateSettings 用の 2 レスポンス
        val active = FixtureLoader.load("subscription_active.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody("[$active]"))
        // settings 更新後は新しい fetchIntervalMinutes を持つ Subscription を返す（180）
        val updatedJson = active.replace("\"fetch_interval_minutes\": 60", "\"fetch_interval_minutes\": 180")
        server.enqueue(MockResponse().setResponseCode(200).setBody(updatedJson))
        val repo = newRepository()
        repo.refresh()
        server.takeRequest() // refresh 分を捨てる
        val before = repo.observeSubscriptions().first().single()
        assertEquals(60, before.fetchIntervalMinutes)

        // Act
        val returned = repo.updateSettings(before.id, 180)

        // Assert: PUT メソッド + パス
        val recorded = server.takeRequest()
        assertEquals("PUT", recorded.method)
        assertEquals(
            "/api/subscriptions/${before.id}/settings",
            recorded.requestUrl?.encodedPath,
        )
        // Assert: ボディに fetch_interval_minutes が含まれる
        val body = recorded.body.readUtf8()
        assertTrue("body should contain fetch_interval_minutes: $body", body.contains("\"fetch_interval_minutes\""))
        assertTrue("body should contain 180: $body", body.contains("180"))
        // 戻り値 + 観測ストリーム双方が新しい値を持つ
        assertEquals(180, returned.fetchIntervalMinutes)
        val afterList = repo.observeSubscriptions().first()
        assertEquals(180, afterList[0].fetchIntervalMinutes)
    }

    @Test
    fun `Issue43 Req 5_1 updateSettings 失敗時は例外を伝搬し購読リストを変えない`() = runTest {
        // Arrange
        val active = FixtureLoader.load("subscription_active.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody("[$active]"))
        val repo = newRepository()
        repo.refresh()
        val before = repo.observeSubscriptions().first().single()

        // updateSettings が 500 で失敗
        val errorBody = """
            {
              "error": {
                "code": "INTERNAL_ERROR",
                "message": "設定の更新に失敗しました。"
              }
            }
        """.trimIndent()
        server.enqueue(
            MockResponse().setResponseCode(500)
                .setHeader("Content-Type", "application/json")
                .setBody(errorBody),
        )

        // Act / Assert
        var thrown: Throwable? = null
        try {
            repo.updateSettings(before.id, 180)
        } catch (e: Throwable) {
            thrown = e
        }
        assertTrue("expected FeedmanException, got $thrown", thrown is FeedmanException)
        // 購読リストは変わらない（fetchIntervalMinutes は元のまま）
        val after = repo.observeSubscriptions().first().single()
        assertEquals(before.fetchIntervalMinutes, after.fetchIntervalMinutes)
    }

    // ===== Issue #43: 購読解除（unsubscribe） =====

    @Test
    fun `Issue43 Req 4_3 unsubscribe で api subscriptions id を DELETE する`() = runTest {
        // Arrange: refresh 用 + unsubscribe 用
        val active = FixtureLoader.load("subscription_active.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody("[$active]"))
        // DELETE は 204 No Content
        server.enqueue(MockResponse().setResponseCode(204))
        val repo = newRepository()
        repo.refresh()
        server.takeRequest() // refresh 分を捨てる
        val before = repo.observeSubscriptions().first().single()

        // Act
        repo.unsubscribe(before.id)

        // Assert: DELETE メソッド + パス
        val recorded = server.takeRequest()
        assertEquals("DELETE", recorded.method)
        assertEquals(
            "/api/subscriptions/${before.id}",
            recorded.requestUrl?.encodedPath,
        )
    }

    @Test
    fun `Issue43 Req 4_4 unsubscribe 成功で観測中のリストから当該フィードが除去される`() = runTest {
        // Arrange: 2 件ロードして 1 件を解除
        val active = FixtureLoader.load("subscription_active.json")
        val errorJson = FixtureLoader.load("subscription_error.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody("[$active,$errorJson]"))
        server.enqueue(MockResponse().setResponseCode(204))
        val repo = newRepository()
        repo.refresh()
        val before = repo.observeSubscriptions().first()
        assertEquals(2, before.size)
        val targetId = before[0].id

        // Act
        repo.unsubscribe(targetId)

        // Assert: 該当 entry がリストから消える
        val after = repo.observeSubscriptions().first()
        assertEquals(1, after.size)
        assertEquals(before[1].id, after[0].id)
    }

    @Test
    fun `Issue43 Req 4_7 unsubscribe 失敗時は例外を伝搬しリストを変えない`() = runTest {
        // Arrange
        val active = FixtureLoader.load("subscription_active.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody("[$active]"))
        val repo = newRepository()
        repo.refresh()
        val before = repo.observeSubscriptions().first().single()

        val errorBody = """
            {
              "error": {
                "code": "INTERNAL_ERROR",
                "message": "解除に失敗しました。"
              }
            }
        """.trimIndent()
        server.enqueue(
            MockResponse().setResponseCode(500)
                .setHeader("Content-Type", "application/json")
                .setBody(errorBody),
        )

        // Act / Assert
        var thrown: Throwable? = null
        try {
            repo.unsubscribe(before.id)
        } catch (e: Throwable) {
            thrown = e
        }
        assertTrue("expected FeedmanException, got $thrown", thrown is FeedmanException)
        // 購読リストは変わらない（1 件残る）
        val after = repo.observeSubscriptions().first()
        assertEquals(1, after.size)
        assertEquals(before.id, after[0].id)
    }

    @Test
    fun `Issue42 Req 4_1 fetch その他のエラー時に例外を伝搬し購読リストを変えない`() = runTest {
        // Arrange
        val active = FixtureLoader.load("subscription_active.json")
        server.enqueue(MockResponse().setResponseCode(200).setBody("[$active]"))
        val repo = newRepository()
        repo.refresh()
        val before = repo.observeSubscriptions().first().single()

        // 503 で失敗
        val errorBody = """
            {
              "error": {
                "code": "UPSTREAM_ERROR",
                "message": "上流サービスでエラーが発生しました。"
              }
            }
        """.trimIndent()
        server.enqueue(
            MockResponse().setResponseCode(503)
                .setHeader("Content-Type", "application/json")
                .setBody(errorBody),
        )

        // Act / Assert
        var thrown: Throwable? = null
        try {
            repo.fetch(before.id)
        } catch (e: Throwable) {
            thrown = e
        }
        assertTrue("expected FeedmanException, got $thrown", thrown is FeedmanException)
        val fe = thrown as FeedmanException
        assertEquals("UPSTREAM_ERROR", fe.code)
        // 購読リストは変わらない
        val after = repo.observeSubscriptions().first().single()
        assertEquals(before.unreadCount, after.unreadCount)
    }
}
