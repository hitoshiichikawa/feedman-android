package com.feedman.android.core.auth

import app.cash.turbine.test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [AuthRepositorySessionStateProvider] の単体テスト（Issue #23 Req 3.3）。
 *
 * - AuthRepository.observeIsAuthenticated() が `false` → LoggedOut
 * - `true` → LoggedIn
 * - 遷移が StateFlow に反映される（exchange 成功で false→true）
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositorySessionStateProviderTest {

    @Test
    fun `Req 3_3 initial isAuthenticated false yields LoggedOut`() = runTest {
        // Arrange
        val repo = FakeAuthRepository(initial = false)
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
        val provider = AuthRepositorySessionStateProvider(authRepository = repo, scope = scope)

        // Act + Assert
        assertEquals(SessionState.LoggedOut, provider.state.value)
        scope.cancel()
    }

    @Test
    fun `Req 3_3 initial isAuthenticated true yields LoggedIn`() = runTest {
        // Arrange
        val repo = FakeAuthRepository(initial = true)
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
        val provider = AuthRepositorySessionStateProvider(authRepository = repo, scope = scope)

        // Act + Assert
        assertEquals(SessionState.LoggedIn, provider.state.value)
        scope.cancel()
    }

    @Test
    fun `Req 3_3 transitions to LoggedIn when AuthRepository emits true`() = runTest {
        // Arrange
        val repo = FakeAuthRepository(initial = false)
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
        val provider = AuthRepositorySessionStateProvider(authRepository = repo, scope = scope)

        // Act + Assert
        provider.state.test {
            assertEquals(SessionState.LoggedOut, awaitItem())
            repo.flip(true)
            assertEquals(SessionState.LoggedIn, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        scope.cancel()
    }

    @Test
    fun `Req 3_3 transitions to LoggedOut when AuthRepository emits false after revoke`() = runTest {
        // Arrange
        val repo = FakeAuthRepository(initial = true)
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
        val provider = AuthRepositorySessionStateProvider(authRepository = repo, scope = scope)

        // Act + Assert
        provider.state.test {
            assertEquals(SessionState.LoggedIn, awaitItem())
            repo.flip(false)
            assertEquals(SessionState.LoggedOut, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        scope.cancel()
    }

    /** 最低限の fake: observeIsAuthenticated だけ機能する。 */
    private class FakeAuthRepository(initial: Boolean) : AuthRepository {
        private val _state = MutableStateFlow(initial)
        fun flip(value: Boolean) {
            _state.value = value
        }

        override suspend fun exchange(authCode: String, codeVerifier: String): ExchangeResult =
            ExchangeResult.Success

        override suspend fun refresh(): RefreshResult = RefreshResult.AuthRequired

        override suspend fun revoke() {
            _state.value = false
        }

        override suspend fun currentUser(): CurrentUserResult =
            CurrentUserResult.Failure(code = "x", httpStatus = null, message = "")

        override fun observeIsAuthenticated(): StateFlow<Boolean> = _state
    }
}
