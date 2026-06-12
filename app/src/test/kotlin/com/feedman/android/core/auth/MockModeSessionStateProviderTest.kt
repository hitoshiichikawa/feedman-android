package com.feedman.android.core.auth

import app.cash.turbine.test
import com.feedman.android.core.model.AppConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Issue #29: `mockMode` 連動の暫定 `SessionStateProvider` 実装テスト（Req 3.5 / NFR 2.2）。
 *
 * - mockMode = false → 起動時に `LoggedOut` を emit（Req 3.1 の前提となる観測）。
 * - mockMode = true  → 起動時に `LoggedIn` を emit（Req 3.2 の前提となる観測）。
 *
 * 本テストは実トークン管理（#24 系）導入前に「シェル差し替えの判断軸が `mockMode` で
 * 駆動できる」ことを担保する。後続 Issue で `SessionStateProvider` の binding を実装に
 * 差し替えても、UI 側（[com.feedman.android.shell.AppShell]）の観測ロジックが
 * `StateFlow<SessionState>` を読むかぎり、本 binding 差し替えだけで Req 3.3 / 3.4 の
 * 遷移挙動も成立する。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MockModeSessionStateProviderTest {

    @Test
    fun `mockMode が false のとき LoggedOut を返す_Req 3_1_3_5`() = runTest {
        // Arrange
        val provider = MockModeSessionStateProvider(
            appConfig = AppConfig(baseUrl = "https://example.invalid", mockMode = false),
        )

        // Act / Assert
        provider.state.test {
            assertEquals(SessionState.LoggedOut, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `mockMode が true のとき LoggedIn を返す_Req 3_2_3_5`() = runTest {
        // Arrange
        val provider = MockModeSessionStateProvider(
            appConfig = AppConfig(baseUrl = "https://example.invalid", mockMode = true),
        )

        // Act / Assert
        provider.state.test {
            assertEquals(SessionState.LoggedIn, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `state は StateFlow として現在値を即時に公開する_NFR 2_2`() = runTest {
        // Arrange
        val provider = MockModeSessionStateProvider(
            appConfig = AppConfig(baseUrl = "https://example.invalid", mockMode = true),
        )

        // Act
        val current = provider.state.value

        // Assert
        assertEquals(SessionState.LoggedIn, current)
    }
}
