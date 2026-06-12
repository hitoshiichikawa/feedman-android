package com.feedman.android.feature.registerfeed

import app.cash.turbine.test
import com.feedman.android.core.data.FeedRegistrationRepository
import com.feedman.android.core.model.Subscription
import com.feedman.android.core.network.FeedmanException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [RegisterFeedViewModel] の単体テスト（Issue #44 / requirements.md AC 1, 2, 3, 4, 5）。
 *
 * 観点:
 * - open / close / updateUrl による表示状態と入力値遷移（Req 1.1 / 1.3 / 1.5）
 * - canSubmit の活性条件（Req 1.4: 空 / 空白のみ → 無効）
 * - 入力変更でエラー解除（Req 2.3 / 5.8）
 * - クライアント側 URL バリデーション（Req 2.1 / 2.4）
 * - submit 成功で RegistrationSucceeded + close（Req 4.1）
 * - submit 失敗時のエラー文言分岐: 409 / 429 (retryAfterSeconds 有/無) / 400 / 500 / NETWORK_ERROR
 * - 送信中状態（Req 3.2）と二重送信抑止
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RegisterFeedViewModelTest {

    private val texts = RegisterFeedErrorTexts(
        duplicate = "[DUP]",
        invalidUrl = "[INVALID]",
        rateLimitWithSeconds = { s -> "[RL=$s]" },
        rateLimitGeneric = "[RL_GENERIC]",
        genericFallback = "[GENERIC]",
        networkUnreachable = "[NETWORK]",
    )
    private val clientInvalidUrl = "[CLIENT_INVALID]"

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── open / close / updateUrl ─────────────────────────────

    @Test
    fun `初期状態は Hidden Req 1_1`() = runTest {
        val vm = RegisterFeedViewModel(StubRepository())
        assertEquals(RegisterFeedUiState.Hidden, vm.uiState.value)
    }

    @Test
    fun `open で Visible 状態にする 入力は空 Req 1_1 Req 1_3`() = runTest {
        val vm = RegisterFeedViewModel(StubRepository())
        vm.open()
        val s = vm.uiState.value as RegisterFeedUiState.Visible
        assertEquals("", s.url)
        assertEquals(false, s.submitInProgress)
        assertEquals(false, s.canSubmit) // 空入力時は送信不可（Req 1.4）
    }

    @Test
    fun `close で Hidden に戻る Req 1_5`() = runTest {
        val vm = RegisterFeedViewModel(StubRepository())
        vm.open()
        vm.close()
        assertEquals(RegisterFeedUiState.Hidden, vm.uiState.value)
    }

    @Test
    fun `updateUrl で入力値が保持され canSubmit が更新される Req 1_3`() = runTest {
        val vm = RegisterFeedViewModel(StubRepository())
        vm.open()
        vm.updateUrl("https://example.com/feed.xml")
        val s = vm.uiState.value as RegisterFeedUiState.Visible
        assertEquals("https://example.com/feed.xml", s.url)
        assertTrue(s.canSubmit)
    }

    @Test
    fun `空白のみの入力では canSubmit が false Req 1_4`() = runTest {
        val vm = RegisterFeedViewModel(StubRepository())
        vm.open()
        vm.updateUrl("   ")
        val s = vm.uiState.value as RegisterFeedUiState.Visible
        assertFalse(s.canSubmit)
    }

    @Test
    fun `updateUrl でクライアントエラーとサーバーエラーが両方クリアされる Req 2_3 Req 5_8`() = runTest {
        val vm = RegisterFeedViewModel(StubRepository())
        vm.setErrorTexts(texts, clientInvalidUrl)
        vm.open()
        // クライアントエラーを誘発
        vm.updateUrl("javascript:alert(1)")
        vm.submit()
        run {
            val s = vm.uiState.value as RegisterFeedUiState.Visible
            assertEquals(clientInvalidUrl, s.clientErrorMessage)
        }

        // 入力変更でクリア
        vm.updateUrl("https://example.com/feed.xml")
        val s2 = vm.uiState.value as RegisterFeedUiState.Visible
        assertNull(s2.clientErrorMessage)
        assertNull(s2.serverErrorMessage)
    }

    // ── クライアント側 URL バリデーション ───────────────────

    @Test
    fun `submit 時に javascript スキーム はクライアントエラーで送信されない Req 2_1`() = runTest {
        val repo = StubRepository()
        val vm = RegisterFeedViewModel(repo)
        vm.setErrorTexts(texts, clientInvalidUrl)
        vm.open()
        vm.updateUrl("javascript:alert(1)")

        vm.submit()

        val s = vm.uiState.value as RegisterFeedUiState.Visible
        assertEquals(clientInvalidUrl, s.clientErrorMessage)
        assertEquals(0, repo.registerCalls) // Repository は呼ばれていない
        assertFalse(s.submitInProgress)
    }

    @Test
    fun `submit 時に http スキームは送信される Req 2_1`() = runTest {
        val repo = StubRepository()
        val vm = RegisterFeedViewModel(repo)
        vm.setErrorTexts(texts, clientInvalidUrl)
        vm.open()
        vm.updateUrl("http://example.com/feed.xml")

        vm.submit()

        assertEquals(1, repo.registerCalls)
    }

    @Test
    fun `submit 時に入力前後の空白は除去された値が送信される Req 2_4`() = runTest {
        val repo = StubRepository()
        val vm = RegisterFeedViewModel(repo)
        vm.setErrorTexts(texts, clientInvalidUrl)
        vm.open()
        vm.updateUrl("  https://example.com/feed.xml  ")

        vm.submit()

        assertEquals("https://example.com/feed.xml", repo.lastRegisterUrl)
    }

    // ── 送信中状態 ──────────────────────────────────────

    @Test
    fun `submit で submitInProgress が true になり再 submit は no-op Req 3_2`() = runTest {
        val repo = StubRepository().apply { suspendRegister = true }
        val vm = RegisterFeedViewModel(repo)
        vm.setErrorTexts(texts, clientInvalidUrl)
        vm.open()
        vm.updateUrl("https://example.com/feed.xml")

        vm.submit() // 1 回目: suspend で停止
        run {
            val s = vm.uiState.value as RegisterFeedUiState.Visible
            assertTrue(s.submitInProgress)
            assertFalse(s.canSubmit) // 進行中は送信ボタン無効
        }
        val callsBefore = repo.registerCalls

        // Act: 2 回目の submit
        vm.submit()

        // Assert: 呼び出し回数は増えていない
        assertEquals(callsBefore, repo.registerCalls)
    }

    // ── 成功時 ────────────────────────────────────────

    @Test
    fun `submit 成功で RegistrationSucceeded が流れシートが閉じる Req 4_1`() = runTest {
        val repo = StubRepository()
        val vm = RegisterFeedViewModel(repo)
        vm.setErrorTexts(texts, clientInvalidUrl)
        vm.open()
        vm.updateUrl("https://example.com/feed.xml")

        vm.events.test {
            vm.submit()
            assertEquals(RegisterFeedEvent.RegistrationSucceeded, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(RegisterFeedUiState.Hidden, vm.uiState.value)
    }

    // ── エラー分岐（Req 5.1〜5.6）─────────────────────

    @Test
    fun `submit 409 で重複登録の文言を表示しシートは閉じない Req 5_1`() = runTest {
        val repo = StubRepository().apply {
            registerException = FeedmanException(
                code = "FEED_ALREADY_REGISTERED",
                errorMessage = "このフィードはすでに登録されています",
                httpStatus = 409,
            )
        }
        val vm = RegisterFeedViewModel(repo)
        vm.setErrorTexts(texts, clientInvalidUrl)
        vm.open()
        vm.updateUrl("https://example.com/feed.xml")

        vm.submit()

        val s = vm.uiState.value as RegisterFeedUiState.Visible
        assertEquals("このフィードはすでに登録されています", s.serverErrorMessage)
        assertFalse(s.submitInProgress) // Req 5.7: 再操作可能
        assertEquals("https://example.com/feed.xml", s.url) // 入力は残る
    }

    @Test
    fun `submit 429 で retryAfterSeconds 付き文言を表示する Req 5_3`() = runTest {
        val repo = StubRepository().apply {
            registerException = FeedmanException(
                code = "REGISTRATION_RATE_LIMIT",
                errorMessage = "rate limit",
                httpStatus = 429,
                retryAfterSeconds = 30,
            )
        }
        val vm = RegisterFeedViewModel(repo)
        vm.setErrorTexts(texts, clientInvalidUrl)
        vm.open()
        vm.updateUrl("https://example.com/feed.xml")

        vm.submit()

        val s = vm.uiState.value as RegisterFeedUiState.Visible
        assertEquals("[RL=30]", s.serverErrorMessage)
    }

    @Test
    fun `submit 429 で retryAfterSeconds が null なら汎用再試行文言 Req 5_4`() = runTest {
        val repo = StubRepository().apply {
            registerException = FeedmanException(
                code = "X",
                errorMessage = "x",
                httpStatus = 429,
                retryAfterSeconds = null,
            )
        }
        val vm = RegisterFeedViewModel(repo)
        vm.setErrorTexts(texts, clientInvalidUrl)
        vm.open()
        vm.updateUrl("https://example.com/feed.xml")

        vm.submit()

        val s = vm.uiState.value as RegisterFeedUiState.Visible
        assertEquals("[RL_GENERIC]", s.serverErrorMessage)
    }

    @Test
    fun `submit 400 で URL 不正文言を表示する Req 5_2`() = runTest {
        val repo = StubRepository().apply {
            registerException = FeedmanException(
                code = "INVALID_FEED_URL",
                errorMessage = "このサイトでフィードを検出できませんでした",
                httpStatus = 400,
            )
        }
        val vm = RegisterFeedViewModel(repo)
        vm.setErrorTexts(texts, clientInvalidUrl)
        vm.open()
        vm.updateUrl("https://example.com/not-a-feed")

        vm.submit()

        val s = vm.uiState.value as RegisterFeedUiState.Visible
        assertEquals("このサイトでフィードを検出できませんでした", s.serverErrorMessage)
    }

    @Test
    fun `submit 500 でサーバー message を表示する Req 5_5`() = runTest {
        val repo = StubRepository().apply {
            registerException = FeedmanException(
                code = "INTERNAL_ERROR",
                errorMessage = "サーバー内部エラー",
                httpStatus = 500,
            )
        }
        val vm = RegisterFeedViewModel(repo)
        vm.setErrorTexts(texts, clientInvalidUrl)
        vm.open()
        vm.updateUrl("https://example.com/feed.xml")

        vm.submit()

        val s = vm.uiState.value as RegisterFeedUiState.Visible
        assertEquals("サーバー内部エラー", s.serverErrorMessage)
    }

    @Test
    fun `submit ネットワーク失敗時にネットワーク文言を表示する Req 5_6`() = runTest {
        val repo = StubRepository().apply {
            registerException = FeedmanException(
                code = FeedmanException.CODE_NETWORK_ERROR,
                errorMessage = FeedmanException.FALLBACK_NETWORK_MESSAGE,
                httpStatus = null,
            )
        }
        val vm = RegisterFeedViewModel(repo)
        vm.setErrorTexts(texts, clientInvalidUrl)
        vm.open()
        vm.updateUrl("https://example.com/feed.xml")

        vm.submit()

        val s = vm.uiState.value as RegisterFeedUiState.Visible
        assertEquals("[NETWORK]", s.serverErrorMessage)
    }

    // ── ヘルパー ─────────────────────────────────────

    private class StubRepository : FeedRegistrationRepository {
        var registerCalls: Int = 0
        var lastRegisterUrl: String? = null
        var registerException: Throwable? = null
        var suspendRegister: Boolean = false

        override suspend fun register(url: String): Subscription {
            registerCalls += 1
            lastRegisterUrl = url
            if (suspendRegister) {
                awaitCancellation()
            }
            registerException?.let { throw it }
            return Subscription(
                id = "sub-1",
                userId = "u",
                feedId = "feed-1",
                feedTitle = "Sample",
                feedUrl = url,
                faviconUrl = null,
                fetchIntervalMinutes = 60,
                feedStatus = "active",
                errorMessage = null,
                unreadCount = 0,
                createdAt = "2026-06-12T00:00:00Z",
            )
        }
    }
}
