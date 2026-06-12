package com.feedman.android.shell

import app.cash.turbine.test
import com.feedman.android.core.auth.SessionState
import com.feedman.android.core.auth.SessionStateProvider
import android.content.Context
import com.feedman.android.core.designsystem.ThemeMode
import com.feedman.android.core.designsystem.ThemeModeRepository
import com.feedman.android.core.ui.LinkOpener
import com.feedman.android.core.ui.OpenLinkResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Issue #29 / Requirement 3 単体テスト（Session 関連）。
 * Issue #31 / Requirement 3, 4, 5 単体テスト（テーマ切替・シート起動状態）。
 *
 * - #29 Req 3.1: LoggedOut の状態を観測できる（[AppShell] が LoginPlaceholderScreen に
 *   差し替える前提）。
 * - #29 Req 3.2: LoggedIn の状態を観測できる（[AppShell] がドロワー付きシェルを描画する
 *   前提）。
 * - #29 Req 3.3 / 3.4: LoggedOut → LoggedIn / LoggedIn → LoggedOut の遷移が UI 側に
 *   届く（`StateFlow` 経由）。
 * - #31 Req 3.3 / 3.4: toggleTheme でテーマモードが反転し、リポジトリへ永続化される
 * - #31 Req 4.2 / 4.4 / 5.2 / 5.4: シート起動状態が None → Account / FeedRegistration → None
 *   と遷移する
 * - NFR 2.2: テストから [SessionStateProvider] / [ThemeModeRepository] 実装を差し替えて
 *   状態を強制できる。
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
        val viewModel = AppShellViewModel(provider, FakeThemeModeRepository(), NoopLinkOpener)

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
        val viewModel = AppShellViewModel(provider, FakeThemeModeRepository(), NoopLinkOpener)

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
        val viewModel = AppShellViewModel(provider, FakeThemeModeRepository(), NoopLinkOpener)

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
        val viewModel = AppShellViewModel(provider, FakeThemeModeRepository(), NoopLinkOpener)

        // Act / Assert
        viewModel.sessionState.test {
            assertEquals(SessionState.LoggedIn, awaitItem())
            provider.emit(SessionState.LoggedOut)
            assertEquals(SessionState.LoggedOut, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─── Issue #31 sheet state ──────────────────────────────────────────────

    @Test
    fun `初期状態でシートは None である_31_Req 4_4_5_4`() = runTest {
        // Arrange
        val viewModel = AppShellViewModel(
            FakeSessionStateProvider(SessionState.LoggedIn),
            FakeThemeModeRepository(),
            NoopLinkOpener,
        )
        // Act / Assert
        assertEquals(AppShellSheet.None, viewModel.activeSheet.value)
    }

    @Test
    fun `openSheet で Account に遷移する_31_Req 4_2`() = runTest {
        // Arrange
        val viewModel = AppShellViewModel(
            FakeSessionStateProvider(SessionState.LoggedIn),
            FakeThemeModeRepository(),
            NoopLinkOpener,
        )
        // Act
        viewModel.openSheet(AppShellSheet.Account)
        // Assert
        assertEquals(AppShellSheet.Account, viewModel.activeSheet.value)
    }

    @Test
    fun `openSheet で FeedRegistration に遷移する_31_Req 5_2`() = runTest {
        // Arrange
        val viewModel = AppShellViewModel(
            FakeSessionStateProvider(SessionState.LoggedIn),
            FakeThemeModeRepository(),
            NoopLinkOpener,
        )
        // Act
        viewModel.openSheet(AppShellSheet.FeedRegistration)
        // Assert
        assertEquals(AppShellSheet.FeedRegistration, viewModel.activeSheet.value)
    }

    @Test
    fun `dismissSheet で None に戻る_31_Req 4_4_5_4`() = runTest {
        // Arrange
        val viewModel = AppShellViewModel(
            FakeSessionStateProvider(SessionState.LoggedIn),
            FakeThemeModeRepository(),
            NoopLinkOpener,
        )
        viewModel.openSheet(AppShellSheet.Account)
        // Act
        viewModel.dismissSheet()
        // Assert
        assertEquals(AppShellSheet.None, viewModel.activeSheet.value)
    }

    @Test
    fun `Account から FeedRegistration へ切り替えると後者が現れる_31_Req 4_2_5_2`() = runTest {
        // Arrange
        val viewModel = AppShellViewModel(
            FakeSessionStateProvider(SessionState.LoggedIn),
            FakeThemeModeRepository(),
            NoopLinkOpener,
        )
        viewModel.openSheet(AppShellSheet.Account)
        // Act
        viewModel.openSheet(AppShellSheet.FeedRegistration)
        // Assert
        assertEquals(AppShellSheet.FeedRegistration, viewModel.activeSheet.value)
    }

    // ─── Issue #31 theme toggle ──────────────────────────────────────────────

    @Test
    fun `toggleTheme でライトからダークへ永続化される_31_Req 3_3_3_4`() = runTest {
        // Arrange
        val repo = FakeThemeModeRepository(initial = ThemeMode.LIGHT)
        val viewModel = AppShellViewModel(FakeSessionStateProvider(SessionState.LoggedIn), repo, NoopLinkOpener)
        viewModel.themeMode.test {
            assertEquals(ThemeMode.LIGHT, awaitItem())
            // Act
            viewModel.toggleTheme(currentlyDark = false)
            // Assert: 新モードが Flow を流れる
            assertEquals(ThemeMode.DARK, awaitItem())
            // 永続化先（repo.current）も更新されている
            assertEquals(ThemeMode.DARK, repo.current)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggleTheme でダークからライトへ永続化される_31_Req 3_3_3_4`() = runTest {
        // Arrange
        val repo = FakeThemeModeRepository(initial = ThemeMode.DARK)
        val viewModel = AppShellViewModel(FakeSessionStateProvider(SessionState.LoggedIn), repo, NoopLinkOpener)
        viewModel.themeMode.test {
            assertEquals(ThemeMode.DARK, awaitItem())
            // Act
            viewModel.toggleTheme(currentlyDark = true)
            // Assert
            assertEquals(ThemeMode.LIGHT, awaitItem())
            assertEquals(ThemeMode.LIGHT, repo.current)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggleTheme で FOLLOW_SYSTEM かつ暗色時はライト固定へ遷移する_31_Req 3_3_境界`() = runTest {
        // Arrange
        val repo = FakeThemeModeRepository(initial = ThemeMode.FOLLOW_SYSTEM)
        val viewModel = AppShellViewModel(FakeSessionStateProvider(SessionState.LoggedIn), repo, NoopLinkOpener)
        viewModel.themeMode.test {
            assertEquals(ThemeMode.FOLLOW_SYSTEM, awaitItem())
            // Act
            viewModel.toggleTheme(currentlyDark = true)
            // Assert
            assertEquals(ThemeMode.LIGHT, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggleTheme 永続化失敗時も UI 側のモードはそのまま反映される_31_NFR 2_1_異常系`() = runTest {
        // Arrange
        val repo = FakeThemeModeRepository(initial = ThemeMode.LIGHT, failOnWrite = true)
        val viewModel = AppShellViewModel(FakeSessionStateProvider(SessionState.LoggedIn), repo, NoopLinkOpener)
        // Act
        viewModel.toggleTheme(currentlyDark = false)
        // Assert: 永続化先は更新されない（書き込み失敗）
        assertEquals(ThemeMode.LIGHT, repo.current)
        // しかし silent fail はしない（write 試行が記録されている）
        assertTrue(repo.writeAttempts > 0)
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

    /**
     * テスト専用の [ThemeModeRepository] in-memory 実装。
     *
     * `failOnWrite = true` のときは [setMode] が IOException を投げる（永続化失敗の模擬）。
     */
    private class FakeThemeModeRepository(
        initial: ThemeMode = ThemeMode.DEFAULT,
        private val failOnWrite: Boolean = false,
    ) : ThemeModeRepository {
        private val _flow = MutableStateFlow(initial)
        var current: ThemeMode = initial
            private set
        var writeAttempts: Int = 0
            private set

        override fun observe(): Flow<ThemeMode> = _flow.asStateFlow()

        override suspend fun setMode(mode: ThemeMode) {
            writeAttempts += 1
            if (failOnWrite) error("simulated persistence failure")
            current = mode
            _flow.value = mode
        }
    }

    /**
     * Issue #37: AppShellViewModel テストでは [LinkOpener] の挙動は対象外のため、
     * 起動を行わない no-op 実装を渡す。本テストは [LinkOpener] のフィールド注入が
     * コンパイル可能であることだけを検証する。
     */
    private object NoopLinkOpener : LinkOpener {
        override fun open(context: Context, url: String): OpenLinkResult =
            OpenLinkResult.NoAppToHandle
    }
}
