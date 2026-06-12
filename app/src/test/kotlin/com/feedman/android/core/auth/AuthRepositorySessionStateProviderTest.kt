package com.feedman.android.core.auth

import app.cash.turbine.test
import com.feedman.android.core.auth.fake.InMemoryTokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [AuthRepositorySessionStateProvider] の単体テスト（Issue #24 / requirements.md）。
 *
 * 復元シーケンスと、復元後の `observeIsAuthenticated()` 追従を検証する:
 *
 * - Req 1.1: 起動直後 Restoring
 * - Req 1.2: 保存トークン + refresh 成功 → LoggedIn
 * - Req 1.3 / NFR 1.2: トークン未保存 → 通信せず LoggedOut
 * - Req 1.4 / NFR 3.1: INVALID_REFRESH_TOKEN → トークン消去 + LoggedOut
 * - Req 1.5: ネットワーク失敗 → 保存トークンを保持したまま LoggedIn フォールバック
 * - NFR 1.1: 5 秒タイムアウト → フォールバック判定
 * - Req 4.3 / 5.3: 復元後の 401 ベース失効で LoggedOut へ遷移
 * - Req 5.3: 復元後の AuthRepository.observeIsAuthenticated() の変化に追従
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositorySessionStateProviderTest {

    @Test
    fun `Req 1_1 initial state is Restoring before restore coroutine resumes`() = runTest {
        // Arrange: Standard dispatcher を使い、launch されたコルーチンを意図的に保留する。
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val tokenStore = InMemoryTokenStore()
        val repo = FakeAuthRepository(initial = false)

        // Act
        val provider = AuthRepositorySessionStateProvider(
            authRepository = repo,
            tokenStore = tokenStore,
            scope = scope,
        )

        // Assert: 復元コルーチンを動かす前は Restoring のまま（Req 1.1）
        assertEquals(SessionState.Restoring, provider.state.value)
        scope.cancel()
    }

    @Test
    fun `Req 1_3 no stored token transitions Restoring to LoggedOut without network call`() = runTest {
        // Arrange: TokenStore は空。FakeAuthRepository.refresh が呼ばれたら失敗させる。
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val tokenStore = InMemoryTokenStore()
        val repo = FakeAuthRepository(
            initial = false,
            refreshResult = RefreshResult.AuthRequired,
        )
        val provider = AuthRepositorySessionStateProvider(
            authRepository = repo,
            tokenStore = tokenStore,
            scope = scope,
        )

        // Act
        advanceUntilIdle()

        // Assert: ネットワーク I/O（refresh 呼び出し）が発生していないこと（NFR 1.2）。
        assertEquals(0, repo.refreshCallCount)
        assertEquals(SessionState.LoggedOut, provider.state.value)
        scope.cancel()
    }

    @Test
    fun `Req 1_2 stored token and refresh success transitions to LoggedIn`() = runTest {
        // Arrange
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val tokenStore = InMemoryTokenStore().also { store ->
            store.save(sampleTokenSet())
        }
        val repo = FakeAuthRepository(
            initial = false,
            refreshResult = RefreshResult.Success,
            // Refresh 成功時に isAuthenticated を true に切り替える挙動を再現
            onRefresh = { it.flip(true) },
        )
        val provider = AuthRepositorySessionStateProvider(
            authRepository = repo,
            tokenStore = tokenStore,
            scope = scope,
        )

        // Act
        advanceUntilIdle()

        // Assert
        assertEquals(1, repo.refreshCallCount)
        assertEquals(SessionState.LoggedIn, provider.state.value)
        scope.cancel()
    }

    @Test
    fun `Req 1_4 INVALID_REFRESH_TOKEN clears tokens and transitions to LoggedOut`() = runTest {
        // Arrange: AuthRepository.refresh が AuthRequired を返すケース。AuthRepositoryImpl 側で
        // TokenStore.clear() + isAuthenticated=false 遷移が起こる契約を再現する。
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val tokenStore = InMemoryTokenStore().also { store ->
            store.save(sampleTokenSet())
        }
        val repo = FakeAuthRepository(
            initial = true,
            refreshResult = RefreshResult.AuthRequired,
            // 認証切れ判定時は TokenStore を消去 + isAuthenticated を false に倒す
            onRefresh = { fake ->
                tokenStore.clear()
                fake.flip(false)
            },
        )
        val provider = AuthRepositorySessionStateProvider(
            authRepository = repo,
            tokenStore = tokenStore,
            scope = scope,
        )

        // Act
        advanceUntilIdle()

        // Assert: トークン消去 + LoggedOut（Req 1.4 / NFR 3.1）
        assertNull(tokenStore.read())
        assertEquals(SessionState.LoggedOut, provider.state.value)
        scope.cancel()
    }

    @Test
    fun `Req 1_5 network failure during refresh keeps token and falls back to LoggedIn`() = runTest {
        // Arrange
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val tokenStore = InMemoryTokenStore().also { store ->
            store.save(sampleTokenSet())
        }
        val repo = FakeAuthRepository(
            initial = false,
            refreshResult = RefreshResult.NetworkFailure(cause = java.io.IOException("offline")),
            // ネットワーク失敗時は AuthRepositoryImpl 契約上 isAuthenticated は変えない
        )
        val provider = AuthRepositorySessionStateProvider(
            authRepository = repo,
            tokenStore = tokenStore,
            scope = scope,
        )

        // Act
        advanceUntilIdle()

        // Assert: TokenStore は保持されたまま、Provider は LoggedIn フォールバック（Req 1.5）
        assertEquals(sampleTokenSet(), tokenStore.read())
        assertEquals(SessionState.LoggedIn, provider.state.value)
        scope.cancel()
    }

    @Test
    fun `NFR 1_1 refresh timeout falls back to LoggedIn when token is stored`() = runTest {
        // Arrange: refresh が常に 5 秒以上ブロックするように設定し、タイムアウト経路を確認する。
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val tokenStore = InMemoryTokenStore().also { store ->
            store.save(sampleTokenSet())
        }
        val repo = FakeAuthRepository(
            initial = false,
            refreshResult = RefreshResult.Success,
            refreshDelayMillis = 10_000L, // 5 秒上限を超える
        )
        val provider = AuthRepositorySessionStateProvider(
            authRepository = repo,
            tokenStore = tokenStore,
            scope = scope,
        )

        // Act: 5 秒上限ギリギリ + α 進める
        advanceTimeBy(6_000L)
        runCurrent()

        // Assert: タイムアウト経由で LoggedIn にフォールバック（保存トークンあり / NFR 1.1）
        assertEquals(SessionState.LoggedIn, provider.state.value)
        scope.cancel()
    }

    @Test
    fun `NFR 1_1 refresh timeout falls back to LoggedOut when token is missing`() = runTest {
        // Arrange: refresh は呼ばれない想定だが、安全側として timeout でも LoggedOut に倒れる経路を確認。
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val tokenStore = InMemoryTokenStore() // 空
        val repo = FakeAuthRepository(
            initial = false,
            refreshResult = RefreshResult.NetworkFailure(cause = java.io.IOException("offline")),
            refreshDelayMillis = 10_000L,
        )
        val provider = AuthRepositorySessionStateProvider(
            authRepository = repo,
            tokenStore = tokenStore,
            scope = scope,
        )

        // Act
        advanceTimeBy(6_000L)
        runCurrent()

        // Assert
        assertEquals(SessionState.LoggedOut, provider.state.value)
        scope.cancel()
    }

    @Test
    fun `Req 4_3 after restore LoggedIn transitions to LoggedOut when isAuthenticated flips to false`() = runTest {
        // Arrange: 復元後の 401 起因の自動ログアウト相当の遷移を再現する。
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val tokenStore = InMemoryTokenStore().also { store ->
            store.save(sampleTokenSet())
        }
        val repo = FakeAuthRepository(
            initial = false,
            refreshResult = RefreshResult.Success,
            onRefresh = { it.flip(true) },
        )
        val provider = AuthRepositorySessionStateProvider(
            authRepository = repo,
            tokenStore = tokenStore,
            scope = scope,
        )

        // Act + Assert
        provider.state.test {
            // 初期 Restoring（init で emit 済み）
            assertEquals(SessionState.Restoring, awaitItem())
            // 復元コルーチン進行
            advanceUntilIdle()
            assertEquals(SessionState.LoggedIn, awaitItem())
            // #22 の 401 起因の自動ログアウトを模倣して isAuthenticated を false に倒す
            tokenStore.clear()
            repo.flip(false)
            advanceUntilIdle()
            assertEquals(SessionState.LoggedOut, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        scope.cancel()
    }

    @Test
    fun `Req 5_3 after restore LoggedOut transitions to LoggedIn when login succeeds`() = runTest {
        // Arrange: 復元時はトークン無しで LoggedOut → ログインフロー成功で LoggedIn に遷移できることを確認。
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val tokenStore = InMemoryTokenStore()
        val repo = FakeAuthRepository(
            initial = false,
            refreshResult = RefreshResult.AuthRequired,
        )
        val provider = AuthRepositorySessionStateProvider(
            authRepository = repo,
            tokenStore = tokenStore,
            scope = scope,
        )

        // Act + Assert
        provider.state.test {
            assertEquals(SessionState.Restoring, awaitItem())
            advanceUntilIdle()
            assertEquals(SessionState.LoggedOut, awaitItem())
            // ログイン成功で AuthRepository.isAuthenticated が true へ
            repo.flip(true)
            advanceUntilIdle()
            assertEquals(SessionState.LoggedIn, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        scope.cancel()
    }

    /** Test helper: 任意の access/refresh トークンを持つ TokenSet。 */
    private fun sampleTokenSet(): TokenSet = TokenSet(
        accessToken = "access-token",
        refreshToken = "refresh-token",
        accessTokenExpiresAtEpochMillis = 0L,
    )

    /**
     * テスト用 FakeAuthRepository。`refresh()` の戻り値・遅延・呼び出し回数を制御し、
     * `observeIsAuthenticated()` は外部から `flip(value)` で遷移させる。
     */
    private class FakeAuthRepository(
        initial: Boolean,
        private val refreshResult: RefreshResult = RefreshResult.AuthRequired,
        private val refreshDelayMillis: Long = 0L,
        private val onRefresh: (suspend (FakeAuthRepository) -> Unit)? = null,
    ) : AuthRepository {
        private val _state = MutableStateFlow(initial)
        var refreshCallCount: Int = 0
            private set

        fun flip(value: Boolean) {
            _state.value = value
        }

        override suspend fun exchange(authCode: String, codeVerifier: String): ExchangeResult =
            ExchangeResult.Success

        override suspend fun refresh(): RefreshResult {
            refreshCallCount += 1
            if (refreshDelayMillis > 0) {
                delay(refreshDelayMillis)
            }
            onRefresh?.invoke(this)
            return refreshResult
        }

        override suspend fun revoke() {
            _state.value = false
        }

        override suspend fun currentUser(): CurrentUserResult =
            CurrentUserResult.Failure(code = "x", httpStatus = null, message = "")

        override fun observeIsAuthenticated(): StateFlow<Boolean> = _state
    }
}
