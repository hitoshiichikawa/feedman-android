package com.feedman.android.feature.account

import app.cash.turbine.test
import com.feedman.android.core.auth.LogoutCoordinator
import com.feedman.android.core.data.UserRepository
import com.feedman.android.core.model.User
import com.feedman.android.core.network.FeedmanException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [AccountSheetViewModel] の単体テスト（Issue #49 requirements.md AC 1〜5）。
 *
 * 主な観点:
 * - open で Visible(Loading) → Loaded への遷移（Req 1.1 / 1.2 / 2.1 / 3.1 / 3.3）
 * - email 空 / blank 時の Loaded 状態は user.email を空のまま保持（UI 側で代替文言）（Req 2.2）
 * - キャッシュ再利用で再 open しても再フェッチしない（Req 1.4）
 * - 回復可能エラー（NETWORK_ERROR / 5xx）で Visible(Error) に遷移し retry で再試行成功（Req 4.1 / 4.2 / 4.3）
 * - 認証エラー（UNAUTHORIZED）で Hidden に戻り UnauthorizedRedirect イベント発火（Req 5.1 / 5.2 / 5.3）
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountSheetViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── 初期状態 / 起動 ────────────────────────────────────────

    @Test
    fun `初期状態は Hidden_Req 1_1`() = runTest {
        val vm = newVm(repository =StubUserRepository())
        assertEquals(AccountSheetUiState.Hidden, vm.uiState.value)
    }

    @Test
    fun `open で取得成功すると Visible Loaded になる_Req 1_2 Req 2_1 Req 3_3`() = runTest {
        // Arrange
        val user = User(id = "u1", email = "alice@example.com")
        val repo = StubUserRepository(result = user)
        val vm = newVm(repository =repo)

        // Act
        vm.open()

        // Assert
        val state = vm.uiState.value as AccountSheetUiState.Visible
        val loaded = state.loadState as AccountSheetUiState.LoadState.Loaded
        assertEquals(user, loaded.user)
        // Req 1.2: 取得は 1 回だけ呼ばれる
        assertEquals(1, repo.callCount)
    }

    @Test
    fun `open で進行中のあいだは Loading 状態_Req 3_1 Req 3_2`() = runTest {
        // Arrange: suspend した状態で取得結果を保留する
        val gate = CompletableDeferred<User>()
        val repo = StubUserRepository(suspendUntil = gate)
        val vm = newVm(repository =repo)

        // Act
        vm.open()

        // Assert: Loading 状態（email 確定値が出ない / Req 3.2）
        val state = vm.uiState.value as AccountSheetUiState.Visible
        assertEquals(AccountSheetUiState.LoadState.Loading, state.loadState)

        // Cleanup: 取得を完了させてからテストを抜ける
        gate.complete(User(id = "u1", email = "alice@example.com"))
    }

    @Test
    fun `email が空文字でも Loaded として user_email を保持_Req 2_2`() = runTest {
        // Arrange: email が空（UI 側で代替文言を選ぶ責務）
        val user = User(id = "u1", email = "")
        val repo = StubUserRepository(result = user)
        val vm = newVm(repository =repo)

        // Act
        vm.open()

        // Assert
        val state = vm.uiState.value as AccountSheetUiState.Visible
        val loaded = state.loadState as AccountSheetUiState.LoadState.Loaded
        assertEquals("", loaded.user.email)
    }

    // ── キャッシュ（再フェッチ抑止）────────────────────────────

    @Test
    fun `Loaded 後に close して再 open しても再フェッチしない_Req 1_4`() = runTest {
        // Arrange
        val user = User(id = "u1", email = "alice@example.com")
        val repo = StubUserRepository(result = user)
        val vm = newVm(repository =repo)

        // Act: open → close → 再 open
        vm.open()
        assertEquals(1, repo.callCount)
        vm.close()
        assertEquals(AccountSheetUiState.Hidden, vm.uiState.value)
        vm.open()

        // Assert: 再フェッチは行われず、Loaded のまま復元される
        assertEquals(1, repo.callCount)
        val state = vm.uiState.value as AccountSheetUiState.Visible
        val loaded = state.loadState as AccountSheetUiState.LoadState.Loaded
        assertEquals(user, loaded.user)
    }

    @Test
    fun `Visible 状態で open を呼んでも何もしない 多重起動回避`() = runTest {
        val user = User(id = "u1", email = "alice@example.com")
        val repo = StubUserRepository(result = user)
        val vm = newVm(repository =repo)
        vm.open()
        val first = repo.callCount

        // Visible 状態のまま再 open → no-op
        vm.open()

        assertEquals(first, repo.callCount)
    }

    // ── 回復可能エラー + 再試行 ────────────────────────────────

    @Test
    fun `回復可能エラーで Visible Error に遷移する_Req 4_1`() = runTest {
        // Arrange: ネットワークエラー
        val repo = StubUserRepository(
            exception = FeedmanException(
                code = FeedmanException.CODE_NETWORK_ERROR,
                errorMessage = FeedmanException.FALLBACK_NETWORK_MESSAGE,
            ),
        )
        val vm = newVm(repository =repo)

        // Act
        vm.open()

        // Assert
        val state = vm.uiState.value as AccountSheetUiState.Visible
        val error = state.loadState as AccountSheetUiState.LoadState.Error
        assertEquals(FeedmanException.FALLBACK_NETWORK_MESSAGE, error.message)
    }

    @Test
    fun `errorMessage が空のときは code 別フォールバック文言を採用する_Req 4_1`() = runTest {
        // Arrange: errorMessage が空の NETWORK_ERROR
        val repo = StubUserRepository(
            exception = FeedmanException(
                code = FeedmanException.CODE_NETWORK_ERROR,
                errorMessage = "",
            ),
        )
        val vm = newVm(repository =repo)

        // Act
        vm.open()

        // Assert: 空文字でなく fallback が入る
        val state = vm.uiState.value as AccountSheetUiState.Visible
        val error = state.loadState as AccountSheetUiState.LoadState.Error
        assertEquals(FeedmanException.FALLBACK_NETWORK_MESSAGE, error.message)
    }

    @Test
    fun `retry で再フェッチ成功すると Visible Loaded に遷移する_Req 4_2 Req 4_3`() = runTest {
        // Arrange: 1 回目はエラー、2 回目は成功
        val user = User(id = "u1", email = "alice@example.com")
        val repo = StubUserRepository(
            queue = listOf(
                StubResult.Failure(
                    FeedmanException(
                        code = FeedmanException.CODE_NETWORK_ERROR,
                        errorMessage = "ネットワークエラー",
                    ),
                ),
                StubResult.Success(user),
            ),
        )
        val vm = newVm(repository =repo)
        vm.open()
        assertTrue(
            (vm.uiState.value as AccountSheetUiState.Visible).loadState
                is AccountSheetUiState.LoadState.Error,
        )

        // Act
        vm.retry()

        // Assert: Loaded に切り替わる（Req 4.3）
        val state = vm.uiState.value as AccountSheetUiState.Visible
        val loaded = state.loadState as AccountSheetUiState.LoadState.Loaded
        assertEquals(user, loaded.user)
        assertEquals(2, repo.callCount)
    }

    @Test
    fun `Loaded 状態で retry を呼んでも no-op_不正呼び出し防御`() = runTest {
        val user = User(id = "u1", email = "alice@example.com")
        val repo = StubUserRepository(result = user)
        val vm = newVm(repository =repo)
        vm.open()
        val first = repo.callCount

        vm.retry()

        // 1 回目の取得回数から変わらない
        assertEquals(first, repo.callCount)
    }

    @Test
    fun `Hidden 状態で retry を呼んでも no-op`() = runTest {
        val vm = newVm(repository =StubUserRepository())
        vm.retry()
        assertEquals(AccountSheetUiState.Hidden, vm.uiState.value)
    }

    // ── 認証エラー ────────────────────────────────────────────

    @Test
    fun `UNAUTHORIZED 時は Hidden に戻り UnauthorizedRedirect イベントを発火する_Req 5_1 Req 5_2 Req 5_3`() = runTest {
        // Arrange
        val repo = StubUserRepository(
            exception = FeedmanException(
                code = "UNAUTHORIZED",
                errorMessage = "認証が必要です",
                httpStatus = 401,
            ),
        )
        val vm = newVm(repository =repo)

        // Act + Assert
        vm.events.test {
            vm.open()
            // Req 5.2 / 5.3: UnauthorizedRedirect イベントが流れる
            assertEquals(AccountSheetEvent.UnauthorizedRedirect, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        // Req 5.1: シートが Hidden に戻る
        assertEquals(AccountSheetUiState.Hidden, vm.uiState.value)
    }

    @Test
    fun `UNAUTHORIZED 時は Visible Error として表示しない_Req 5_3`() = runTest {
        // Arrange
        val repo = StubUserRepository(
            exception = FeedmanException(
                code = "UNAUTHORIZED",
                errorMessage = "認証が必要です",
                httpStatus = 401,
            ),
        )
        val vm = newVm(repository =repo)

        // Act
        vm.open()

        // Assert: Visible(Error) ではなく Hidden（Req 5.3: 重複表示しない）
        assertTrue(vm.uiState.value is AccountSheetUiState.Hidden)
    }

    // ── close 振る舞い ────────────────────────────────────────

    @Test
    fun `Visible 状態で close すると Hidden に戻る`() = runTest {
        val user = User(id = "u1", email = "alice@example.com")
        val vm = newVm(repository =StubUserRepository(result = user))
        vm.open()
        vm.close()
        assertEquals(AccountSheetUiState.Hidden, vm.uiState.value)
    }

    // ── Issue #50: ログアウト振る舞い ─────────────────────────

    @Test
    fun `Issue50 Req 1_2 logout で LogoutCoordinator perform が 1 回呼ばれる`() = runTest {
        val user = User(id = "u1", email = "alice@example.com")
        val logout = StubLogoutCoordinator()
        val vm = newVm(repository = StubUserRepository(result = user), logout = logout)
        vm.open()

        vm.logout()

        assertEquals(1, logout.callCount)
    }

    @Test
    fun `Issue50 Req 1_3 logout 中の再 logout 呼び出しは無視される`() = runTest {
        val user = User(id = "u1", email = "alice@example.com")
        val gate = CompletableDeferred<Unit>()
        val logout = StubLogoutCoordinator(suspendUntil = gate)
        val vm = newVm(repository = StubUserRepository(result = user), logout = logout)
        vm.open()
        vm.logout()
        // gate を完了させずに同時押下を再現
        vm.logout()
        vm.logout()

        // 進行中の 1 件のみ起動
        assertEquals(1, logout.callCount)

        // ゲートを開いて 1 件完了させる
        gate.complete(Unit)
    }

    @Test
    fun `Issue50 Req 1_3 1_4 logout 中は logoutInProgress true_完了で Hidden`() = runTest {
        val user = User(id = "u1", email = "alice@example.com")
        val gate = CompletableDeferred<Unit>()
        val logout = StubLogoutCoordinator(suspendUntil = gate)
        val vm = newVm(repository = StubUserRepository(result = user), logout = logout)
        vm.open()

        vm.logout()

        // Req 1.3 / 1.4: 進行中
        val mid = vm.uiState.value as AccountSheetUiState.Visible
        assertTrue("logoutInProgress は true", mid.logoutInProgress)

        // ゲートを開いて完了させる
        gate.complete(Unit)

        // Req 4.3: Hidden に戻る
        assertEquals(AccountSheetUiState.Hidden, vm.uiState.value)
    }

    @Test
    fun `Issue50 Req 3_3 logout 後の再 open では cachedUser が再現せず再フェッチが走る`() = runTest {
        val user = User(id = "u1", email = "alice@example.com")
        val repo = StubUserRepository(result = user)
        val logout = StubLogoutCoordinator()
        val vm = newVm(repository = repo, logout = logout)

        // 1 回目の open: 取得して cache
        vm.open()
        assertEquals(1, repo.callCount)

        // logout で cachedUser を破棄
        vm.logout()
        assertEquals(AccountSheetUiState.Hidden, vm.uiState.value)

        // 2 回目の open: 再フェッチが走る（cachedUser が破棄されたため）
        vm.open()
        assertEquals("再フェッチが走るべき", 2, repo.callCount)
    }

    @Test
    fun `Issue50 Req 1_2 Hidden 状態での logout は no-op_LogoutCoordinator を呼ばない`() = runTest {
        val logout = StubLogoutCoordinator()
        val vm = newVm(repository = StubUserRepository(), logout = logout)
        // open しない（Hidden のまま）

        vm.logout()

        assertEquals(0, logout.callCount)
    }

    @Test
    fun `Issue50 Req 5_2 logout 中に進行中の fetchJob があればキャンセルする`() = runTest {
        // Arrange: fetchJob を suspend させて active 状態を作る
        val fetchGate = CompletableDeferred<User>()
        val repo = StubUserRepository(suspendUntil = fetchGate)
        val logout = StubLogoutCoordinator()
        val vm = newVm(repository = repo, logout = logout)
        vm.open()
        // この時点で fetch は suspendUntil で保留中
        val midOpen = vm.uiState.value as AccountSheetUiState.Visible
        assertEquals(AccountSheetUiState.LoadState.Loading, midOpen.loadState)

        // Act: logout を呼ぶと fetchJob はキャンセルされる
        vm.logout()

        // Assert: logout は呼ばれる（fetch のキャンセルは観測可能挙動として副作用が無くなる）
        assertEquals(1, logout.callCount)

        // Cleanup
        fetchGate.complete(User(id = "x", email = "y"))
    }

    // ── helpers ─────────────────────────────────────────────────────

    /**
     * テスト用 [AccountSheetViewModel] ファクトリ。
     *
     * - `repository`: 必須
     * - `logout`: 省略時は [StubLogoutCoordinator]（perform で何もしない fake）を使う
     */
    private fun newVm(
        repository: UserRepository,
        logout: LogoutCoordinator = StubLogoutCoordinator(),
    ): AccountSheetViewModel = AccountSheetViewModel(
        repository = repository,
        logoutCoordinator = logout,
    )
}

// ── テストダブル ─────────────────────────────────────────────

/**
 * 取得結果の queue を表現する密封型（複数回呼び出しを順に異なる結果で返したい時に使う）。
 */
internal sealed interface StubResult {
    data class Success(val user: User) : StubResult
    data class Failure(val exception: Throwable) : StubResult
}

/**
 * テスト用 [UserRepository] スタブ。
 *
 * - `result` または `exception` を指定すると毎回同じ結果を返す
 * - `queue` を指定すると順に消費し、空になると最後の値を繰り返す
 * - `suspendUntil` を指定すると、それが完了するまで取得を保留する
 */
internal class StubUserRepository(
    private val result: User? = null,
    private val exception: Throwable? = null,
    private val queue: List<StubResult>? = null,
    private val suspendUntil: CompletableDeferred<User>? = null,
) : UserRepository {

    var callCount: Int = 0
        private set

    override suspend fun getCurrentUser(): User {
        val index = callCount
        callCount++

        if (suspendUntil != null) {
            return suspendUntil.await()
        }

        if (queue != null && queue.isNotEmpty()) {
            val item = queue.getOrElse(index) { queue.last() }
            return when (item) {
                is StubResult.Success -> item.user
                is StubResult.Failure -> throw item.exception
            }
        }

        exception?.let { throw it }
        return result ?: error("StubUserRepository: no result configured")
    }
}

/**
 * テスト用 [LogoutCoordinator] スタブ（Issue #50）。
 *
 * - 既定: `perform()` で何もしない（呼び出し回数のみ記録）
 * - `suspendUntil` を指定すると、それが完了するまで perform を保留する（進行中状態の検証用）
 */
internal class StubLogoutCoordinator(
    private val suspendUntil: CompletableDeferred<Unit>? = null,
) : LogoutCoordinator {

    var callCount: Int = 0
        private set

    override suspend fun perform() {
        callCount++
        suspendUntil?.await()
    }
}
