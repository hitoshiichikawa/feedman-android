package com.feedman.android.feature.login

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.feedman.android.core.auth.AuthRepository
import com.feedman.android.core.auth.CurrentUserResult
import com.feedman.android.core.auth.ExchangeResult
import com.feedman.android.core.auth.PkceGenerator
import com.feedman.android.core.auth.PkcePair
import com.feedman.android.core.auth.RefreshResult
import com.feedman.android.core.model.AppConfig
import com.feedman.android.core.network.FeedmanException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
 * [LoginViewModel] の単体テスト（Issue #23 / Req 2.1〜2.5 / 3.1〜3.5 / 4.1〜4.4 / 5.1〜5.3）。
 *
 * 検証ポイント:
 * - 押下開始時に PKCE pair を生成し code_verifier を SavedStateHandle に保存（Req 2.4）
 * - 押下開始時に AuthorizationUrlBuilder が組み立てた URL を openCustomTabs に流す（Req 2.1〜2.3）
 * - 進行中（LaunchingCustomTabs / Exchanging）の二重押下を抑止（Req 2.5 / 3.5）
 * - feedman://auth/callback?auth_code=... 受領時に AuthRepository.exchange を呼ぶ（Req 3.1, 3.2）
 * - exchange 成功時に code_verifier 破棄 + Idle に戻る（Req 3.3, 3.4 / NFR 1.3）
 * - exchange サーバーエラー時に LoginUiState.Error.Server に遷移（Req 4.1）
 * - exchange ネットワーク失敗時に LoginUiState.Error.Network に遷移（Req 4.2）
 * - 失敗後の再押下で新しい code_verifier を生成（Req 4.3 / 4.4 / 5.2）
 * - auth_code が含まれないコールバックでは exchange を呼ばず Idle のまま（Req 5.1, 5.3 / NFR 2.2）
 * - スキーム不一致のコールバックでは exchange を呼ばず Idle のまま（NFR 2.1）
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private lateinit var fakePkce: FakePkceGenerator
    private lateinit var fakeRepo: FakeAuthRepository
    private val appConfig = AppConfig(baseUrl = "https://example.invalid", mockMode = false)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fakePkce = FakePkceGenerator()
        fakeRepo = FakeAuthRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()): LoginViewModel =
        LoginViewModel(
            savedStateHandle = handle,
            pkceGenerator = fakePkce,
            authRepository = fakeRepo,
            appConfig = appConfig,
        )

    // ── Req 2: 押下開始 ──────────────────────────────────────────────

    @Test
    fun `Req 2_4 startGoogleLogin stores generated code_verifier in SavedStateHandle`() = runTest {
        // Arrange
        val handle = SavedStateHandle()
        fakePkce.next = PkcePair(codeVerifier = "verifier-A", codeChallenge = "challenge-A")
        val vm = viewModel(handle)

        // Act
        vm.startGoogleLogin()

        // Assert
        assertEquals(
            "verifier-A",
            handle.get<String>(LoginViewModel.KEY_CODE_VERIFIER),
        )
    }

    @Test
    fun `Req 2_1 to 2_3 startGoogleLogin emits authorization URL with flow=native and code_challenge`() = runTest {
        // Arrange
        fakePkce.next = PkcePair(codeVerifier = "v", codeChallenge = "C")
        val vm = viewModel()

        // Act + Assert
        vm.openCustomTabs.test {
            vm.startGoogleLogin()
            val url = awaitItem()
            assertEquals(
                "https://example.invalid/auth/google/login?flow=native&code_challenge=C&code_challenge_method=S256",
                url,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Req 2_5 startGoogleLogin while LaunchingCustomTabs is no-op`() = runTest {
        // Arrange
        fakePkce.next = PkcePair(codeVerifier = "v1", codeChallenge = "c1")
        val vm = viewModel()
        vm.startGoogleLogin()

        // Sanity: 1 回目で LaunchingCustomTabs
        assertEquals(LoginUiState.LaunchingCustomTabs, vm.uiState.value)

        // 2 回目の押下は新しい PKCE を生成しないし、URL も流れない
        val verifierBefore = (vm as Any).hashCode() // placeholder for compile
        fakePkce.next = PkcePair(codeVerifier = "v2-should-not-be-used", codeChallenge = "c2")

        // Act
        vm.startGoogleLogin()

        // Assert: PKCE generator は 2 回目で呼ばれていない
        assertEquals(1, fakePkce.callCount)
    }

    @Test
    fun `Req 1_1 initial uiState is Idle`() = runTest {
        // Arrange + Act
        val vm = viewModel()

        // Assert
        assertEquals(LoginUiState.Idle, vm.uiState.value)
    }

    // ── Req 3: コールバック受領 ─────────────────────────────────────

    @Test
    fun `Req 3_2 onDeepLink success calls AuthRepository_exchange with stored verifier`() = runTest {
        // Arrange
        val handle = SavedStateHandle()
        fakePkce.next = PkcePair(codeVerifier = "verifier-AAA", codeChallenge = "C")
        val vm = viewModel(handle)
        vm.startGoogleLogin()
        fakeRepo.exchangeResult = ExchangeResult.Success

        // Act
        vm.onDeepLink("feedman://auth/callback?auth_code=one-time-XYZ")

        // Assert
        assertEquals(1, fakeRepo.exchangeCalls.size)
        assertEquals("one-time-XYZ", fakeRepo.exchangeCalls[0].first)
        assertEquals("verifier-AAA", fakeRepo.exchangeCalls[0].second)
    }

    @Test
    fun `Req 3_4 NFR 1_3 onDeepLink success clears stored verifier`() = runTest {
        // Arrange
        val handle = SavedStateHandle()
        fakePkce.next = PkcePair(codeVerifier = "verifier-clear", codeChallenge = "C")
        val vm = viewModel(handle)
        vm.startGoogleLogin()
        fakeRepo.exchangeResult = ExchangeResult.Success

        // Act
        vm.onDeepLink("feedman://auth/callback?auth_code=ok")

        // Assert
        assertNull(handle.get<String>(LoginViewModel.KEY_CODE_VERIFIER))
    }

    @Test
    fun `Req 3_3 onDeepLink success transitions uiState to Idle`() = runTest {
        // Arrange
        fakePkce.next = PkcePair(codeVerifier = "v", codeChallenge = "C")
        val vm = viewModel()
        vm.startGoogleLogin()
        fakeRepo.exchangeResult = ExchangeResult.Success

        // Act
        vm.onDeepLink("feedman://auth/callback?auth_code=ok")

        // Assert
        // SessionStateProvider 経由で AppShell が LoggedIn に切替わるため、LoginViewModel 側は
        // Idle に戻して終了する（ローカル UI は AppShell の切替で消える）。
        assertEquals(LoginUiState.Idle, vm.uiState.value)
    }

    // ── Req 4: 失敗 ─────────────────────────────────────────────────

    @Test
    fun `Req 4_1 onDeepLink INVALID_GRANT transitions to Error_Server`() = runTest {
        // Arrange
        fakePkce.next = PkcePair(codeVerifier = "v", codeChallenge = "C")
        val vm = viewModel()
        vm.startGoogleLogin()
        fakeRepo.exchangeResult = ExchangeResult.ServerError(
            code = "INVALID_GRANT",
            httpStatus = 400,
            message = "auth_code expired or reused",
        )

        // Act
        vm.onDeepLink("feedman://auth/callback?auth_code=bad")

        // Assert
        val state = vm.uiState.value
        assertTrue("expected Error, got $state", state is LoginUiState.Error)
        val server = (state as LoginUiState.Error).error
        assertTrue("expected Server error", server is LoginError.Server)
        assertTrue("INVALID_GRANT should be classified", (server as LoginError.Server).isInvalidGrant())
    }

    @Test
    fun `Req 4_2 onDeepLink network failure transitions to Error_Network`() = runTest {
        // Arrange
        fakePkce.next = PkcePair(codeVerifier = "v", codeChallenge = "C")
        val vm = viewModel()
        vm.startGoogleLogin()
        fakeRepo.exchangeResult = ExchangeResult.NetworkFailure(
            FeedmanException(
                code = FeedmanException.CODE_NETWORK_ERROR,
                errorMessage = FeedmanException.FALLBACK_NETWORK_MESSAGE,
            ),
        )

        // Act
        vm.onDeepLink("feedman://auth/callback?auth_code=ok")

        // Assert
        val state = vm.uiState.value
        assertTrue("expected Error.Network, got $state", state is LoginUiState.Error)
        assertEquals(LoginError.Network, (state as LoginUiState.Error).error)
    }

    @Test
    fun `Req 4_4 NFR 1_3 onDeepLink failure clears stored verifier`() = runTest {
        // Arrange
        val handle = SavedStateHandle()
        fakePkce.next = PkcePair(codeVerifier = "verifier-keep-no", codeChallenge = "C")
        val vm = viewModel(handle)
        vm.startGoogleLogin()
        fakeRepo.exchangeResult = ExchangeResult.ServerError(
            code = "INVALID_GRANT",
            httpStatus = 400,
            message = "x",
        )

        // Act
        vm.onDeepLink("feedman://auth/callback?auth_code=bad")

        // Assert: 失敗でも code_verifier は残さない（NFR 1.3 / Req 4.4）
        assertNull(handle.get<String>(LoginViewModel.KEY_CODE_VERIFIER))
    }

    @Test
    fun `Req 4_3 startGoogleLogin after Error generates fresh code_verifier`() = runTest {
        // Arrange
        val handle = SavedStateHandle()
        fakePkce.next = PkcePair(codeVerifier = "first-v", codeChallenge = "C1")
        val vm = viewModel(handle)
        vm.startGoogleLogin()
        fakeRepo.exchangeResult = ExchangeResult.ServerError(
            code = "INVALID_GRANT",
            httpStatus = 400,
            message = "x",
        )
        vm.onDeepLink("feedman://auth/callback?auth_code=bad")
        // Sanity: Error 状態
        assertTrue(vm.uiState.value is LoginUiState.Error)

        fakePkce.next = PkcePair(codeVerifier = "second-v", codeChallenge = "C2")

        // Act
        vm.startGoogleLogin()

        // Assert: 新しい verifier が保存され、PKCE 生成が 2 回呼ばれた
        assertEquals("second-v", handle.get<String>(LoginViewModel.KEY_CODE_VERIFIER))
        assertEquals(2, fakePkce.callCount)
    }

    // ── Req 5 / NFR 2: ディープリンク無し / 不正 ───────────────────

    @Test
    fun `Req 5_3 NFR 2_2 onDeepLink without auth_code does not call exchange`() = runTest {
        // Arrange
        fakePkce.next = PkcePair(codeVerifier = "v", codeChallenge = "C")
        val vm = viewModel()
        vm.startGoogleLogin()

        // Act: auth_code が無い不完全なコールバック
        vm.onDeepLink("feedman://auth/callback?other=1")

        // Assert
        assertEquals(0, fakeRepo.exchangeCalls.size)
        // LaunchingCustomTabs 状態を維持（Req 5.1: エラーメッセージは出さない）
        assertTrue(vm.uiState.value !is LoginUiState.Error)
    }

    @Test
    fun `NFR 2_1 onDeepLink with non-feedman scheme does not call exchange`() = runTest {
        // Arrange
        fakePkce.next = PkcePair(codeVerifier = "v", codeChallenge = "C")
        val vm = viewModel()
        vm.startGoogleLogin()

        // Act
        vm.onDeepLink("https://example.com/auth/callback?auth_code=evil")

        // Assert
        assertEquals(0, fakeRepo.exchangeCalls.size)
    }

    @Test
    fun `NFR 2_1 onDeepLink with wrong host or path does not call exchange`() = runTest {
        // Arrange
        fakePkce.next = PkcePair(codeVerifier = "v", codeChallenge = "C")
        val vm = viewModel()
        vm.startGoogleLogin()

        // Act
        vm.onDeepLink("feedman://other/path?auth_code=ok")

        // Assert
        assertEquals(0, fakeRepo.exchangeCalls.size)
    }

    @Test
    fun `Req 5_3 onDeepLink without saved verifier does not call exchange`() = runTest {
        // Arrange: startGoogleLogin を呼ばずにいきなりディープリンクが届くケース
        val vm = viewModel(SavedStateHandle())

        // Act
        vm.onDeepLink("feedman://auth/callback?auth_code=zzz")

        // Assert
        assertEquals(0, fakeRepo.exchangeCalls.size)
    }

    // ── Req 3.5: exchange 進行中の二重押下抑止 ────────────────────

    @Test
    fun `Req 3_5 startGoogleLogin while Exchanging is no-op`() = runTest {
        // Arrange
        fakePkce.next = PkcePair(codeVerifier = "v", codeChallenge = "C")
        val vm = viewModel()
        vm.startGoogleLogin()
        // exchange を「中断」させて Exchanging 状態を維持するため、suspending repo を用意
        fakeRepo.suspendExchange = true

        // Act: deeplink 受領 → Exchanging に遷移 → さらにボタン押下
        vm.onDeepLink("feedman://auth/callback?auth_code=ok")
        assertEquals(LoginUiState.Exchanging, vm.uiState.value)
        fakePkce.next = PkcePair(codeVerifier = "should-not-be-used", codeChallenge = "X")

        vm.startGoogleLogin()

        // Assert: 2 回目の startGoogleLogin は no-op（生成は 1 回のまま）
        assertEquals(1, fakePkce.callCount)
    }

    // ── helpers ────────────────────────────────────────────────────

    /** PkceGenerator の fake。`next` を都度差し替えて生成内容を制御する。 */
    private class FakePkceGenerator : PkceGenerator {
        var next: PkcePair = PkcePair(codeVerifier = "default-v", codeChallenge = "default-c")
        var callCount: Int = 0
        override fun generate(): PkcePair {
            callCount += 1
            return next
        }
    }

    /**
     * AuthRepository の fake。本テストは exchange のみ呼び出すため、それ以外は no-op。
     *
     * - `exchangeResult` を都度差し替えて応答を制御
     * - `suspendExchange = true` の場合、exchange は永遠に await し続ける
     *   （LoginUiState.Exchanging を維持して二重押下を検証する用途）
     */
    private class FakeAuthRepository : AuthRepository {
        var exchangeResult: ExchangeResult = ExchangeResult.Success
        var suspendExchange: Boolean = false
        val exchangeCalls = mutableListOf<Pair<String, String>>()
        private val _isAuthenticated = MutableStateFlow(false)

        override suspend fun exchange(authCode: String, codeVerifier: String): ExchangeResult {
            exchangeCalls += authCode to codeVerifier
            if (suspendExchange) {
                kotlinx.coroutines.awaitCancellation()
            }
            val result = exchangeResult
            if (result is ExchangeResult.Success) {
                _isAuthenticated.value = true
            }
            return result
        }

        override suspend fun refresh(): RefreshResult = RefreshResult.AuthRequired

        override suspend fun revoke() {
            _isAuthenticated.value = false
        }

        override suspend fun currentUser(): CurrentUserResult =
            CurrentUserResult.Failure(code = "x", httpStatus = null, message = "not implemented in fake")

        override fun observeIsAuthenticated(): StateFlow<Boolean> = _isAuthenticated
    }
}
