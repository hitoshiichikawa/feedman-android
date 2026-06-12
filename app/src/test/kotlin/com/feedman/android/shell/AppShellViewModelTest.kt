package com.feedman.android.shell

import app.cash.turbine.test
import com.feedman.android.core.auth.SessionState
import com.feedman.android.core.auth.SessionStateProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Issue #29 / Requirement 3 単体テスト。
 *
 * - Req 3.1: LoggedOut の状態を観測できる（[AppShell] が LoginPlaceholderScreen に
 *   差し替える前提）。
 * - Req 3.2: LoggedIn の状態を観測できる（[AppShell] がドロワー付きシェルを描画する
 *   前提）。
 * - Req 3.3 / 3.4: LoggedOut → LoggedIn / LoggedIn → LoggedOut の遷移が UI 側に
 *   届く（`StateFlow` 経由）。
 * - NFR 2.2: テストから [SessionStateProvider] 実装を差し替えて状態を強制できる。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppShellViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `LoggedOut を返す Provider を渡すと初期 state が LoggedOut になる_Req 3_1`() = runTest {
        // Arrange
        val provider = FakeSessionStateProvider(SessionState.LoggedOut)
        val viewModel = AppShellViewModel(provider)

        // Act / Assert
        viewModel.sessionState.test {
            assertEquals(SessionState.LoggedOut, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `LoggedIn を返す Provider を渡すと初期 state が LoggedIn になる_Req 3_2`() = runTest {
        // Arrange
        val provider = FakeSessionStateProvider(SessionState.LoggedIn)
        val viewModel = AppShellViewModel(provider)

        // Act / Assert
        viewModel.sessionState.test {
            assertEquals(SessionState.LoggedIn, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `LoggedOut から LoggedIn への遷移が観測される_Req 3_3`() = runTest {
        // Arrange
        val provider = FakeSessionStateProvider(SessionState.LoggedOut)
        val viewModel = AppShellViewModel(provider)

        // Act / Assert
        viewModel.sessionState.test {
            assertEquals(SessionState.LoggedOut, awaitItem())
            provider.emit(SessionState.LoggedIn)
            assertEquals(SessionState.LoggedIn, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `LoggedIn から LoggedOut への遷移が観測される_Req 3_4`() = runTest {
        // Arrange
        val provider = FakeSessionStateProvider(SessionState.LoggedIn)
        val viewModel = AppShellViewModel(provider)

        // Act / Assert
        viewModel.sessionState.test {
            assertEquals(SessionState.LoggedIn, awaitItem())
            provider.emit(SessionState.LoggedOut)
            assertEquals(SessionState.LoggedOut, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * テスト専用の [SessionStateProvider] 実装（NFR 2.2 「差し替え可能な依存」の検証）。
     */
    private class FakeSessionStateProvider(initial: SessionState) : SessionStateProvider {
        private val _state = MutableStateFlow(initial)
        override val state: StateFlow<SessionState> = _state.asStateFlow()
        fun emit(next: SessionState) {
            _state.value = next
        }
    }
}
