package com.feedman.android.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feedman.android.core.auth.AccountDeletionCoordinator
import com.feedman.android.core.auth.DeletionResult
import com.feedman.android.core.auth.LogoutCoordinator
import com.feedman.android.core.data.UserRepository
import com.feedman.android.core.network.FeedmanException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * アカウントシート（Issue #49 / SPEC §5.7）の ViewModel。
 *
 * シートはアプリ全体で 1 つ起動できる前提（AppShell 配下に配置）。`open` で起動時に
 * 1 回だけ現在ユーザー取得を開始し（Req 1.2）、`close` で Hidden に戻す。
 *
 * 状態モデル:
 * - [uiState]: シートの表示状態（[AccountSheetUiState.Hidden] / [AccountSheetUiState.Visible]）
 *   - Visible 内の [AccountSheetUiState.LoadState] でユーザー領域のロード状態（Loading /
 *     Loaded / Error）を表現する（Req 2, 3, 4）。
 * - [events]: 認証切れ時の one-shot 通知（Req 5.1〜5.3）
 *
 * ## キャッシュ規約（Req 1.4）
 *
 * 一度 [AccountSheetUiState.LoadState.Loaded] になった User は ViewModel 内で保持され、
 * 同一 ViewModel インスタンスで [open] が再度呼ばれても **再フェッチを行わない**。
 * Visible 状態を Loaded のまま復元する（観測可能挙動として「再オープン時にローディングが
 * 出ない」）。
 *
 * エラー状態 / Loading 状態のときに再 [open] された場合は、現在の取得を温存する
 * （多重起動・多重フェッチを避ける）。
 *
 * ## 認証切れの分岐（Req 5.1〜5.3）
 *
 * FeedmanException が `code = "UNAUTHORIZED"` を持つときは:
 * 1. シートを [AccountSheetUiState.Hidden] に戻す（Req 5.1）
 * 2. [AccountSheetEvent.UnauthorizedRedirect] を流す（Req 5.2 / 5.3）
 * 3. 通常エラー表示（Req 4.1）には積まない（Req 5.3 の重複表示回避）
 *
 * 実際のログイン画面遷移は SessionStateProvider 側で表現する責務であり、本 ViewModel は
 * イベント通知のみを担う（AppShell が SessionState を観測してログイン画面に切り替える）。
 */
@HiltViewModel
class AccountSheetViewModel @Inject constructor(
    private val repository: UserRepository,
    private val logoutCoordinator: LogoutCoordinator,
    private val accountDeletionCoordinator: AccountDeletionCoordinator,
) : ViewModel() {

    private val _uiState: MutableStateFlow<AccountSheetUiState> =
        MutableStateFlow(AccountSheetUiState.Hidden)
    val uiState: StateFlow<AccountSheetUiState> = _uiState.asStateFlow()

    private val _events: MutableSharedFlow<AccountSheetEvent> = MutableSharedFlow(
        replay = 0,
        extraBufferCapacity = 4,
    )
    val events: SharedFlow<AccountSheetEvent> = _events.asSharedFlow()

    /** 現在進行中の取得ジョブ。多重 [open] / [retry] 時の重複起動を防ぐ。 */
    private var fetchJob: Job? = null

    /** Req 1.4: 取得済み User を保持し、再 [open] 時の再フェッチを抑止する。 */
    private var cachedUser: com.feedman.android.core.model.User? = null

    /** Issue #50 Req 1.2 / 1.3: ログアウト進行中のジョブ。多重起動を防ぐ。 */
    private var logoutJob: Job? = null

    /** Issue #51 Req 3.2 / 3.3: 退会送信中のジョブ。多重起動を防ぐ。 */
    private var deletionJob: Job? = null

    /**
     * Req 1.1 / 1.2 / 1.4: ドロワーフッタのアカウント項目から呼ばれて、シートを開く。
     *
     * - 既に [AccountSheetUiState.Visible] のときは何もしない（多重起動回避）
     * - キャッシュ済み User があれば Visible(Loaded) として復元（Req 1.4: 再フェッチしない）
     * - 未取得なら Loading に遷移し、取得を 1 回開始する（Req 1.2 / 3.1）
     */
    fun open() {
        // Visible 状態のときは何もしない（多重起動を避ける）
        if (_uiState.value is AccountSheetUiState.Visible) return

        val cached = cachedUser
        if (cached != null) {
            // Req 1.4: 既に取得済みのユーザー情報を再利用して再フェッチを行わない
            _uiState.value = AccountSheetUiState.Visible(
                loadState = AccountSheetUiState.LoadState.Loaded(cached),
            )
            return
        }

        startFetch()
    }

    /** Req 1.x: クローズ操作（ドラッグ下げ / スクリム / ハードウェアバック）。 */
    fun close() {
        // 進行中の取得はキャンセル（次回 open で再開）
        fetchJob?.cancel()
        fetchJob = null
        _uiState.value = AccountSheetUiState.Hidden
    }

    /**
     * Issue #50 Req 1.2 / 1.3 / 1.4 / 4.3 / 5.2: ログアウト処理を開始する。
     *
     * 観測可能挙動:
     * 1. 現在 [AccountSheetUiState.Visible] であれば、`logoutInProgress = true` に切り替える
     *    （Req 1.3 ボタン disabled / Req 1.4 ローディング表現）
     * 2. [LogoutCoordinator.perform] を 1 回呼び出す（Req 2.1 / 2.2 / 2.3 / 2.4 / 3.1 / 5.1）
     * 3. 完了後、Hidden に戻し cachedUser を破棄する（Req 3.3 / 4.3）
     * 4. 進行中の現在ユーザー取得ジョブをキャンセルする（互いに矛盾する状態を作らないため）
     *
     * 多重押下対策: 既に [logoutJob] が active な間は no-op（Req 1.3 ボタン disabled の補強）。
     *
     * 例外モデル: [LogoutCoordinator.perform] は例外を投げない契約のため、本メソッド内でも
     * try/catch を行わない。万一 throw された場合でも `finally` で `logoutInProgress = false`
     * 相当（Hidden）に戻す経路を確保する（防衛的）。
     *
     * SessionState 遷移は本メソッドでは行わず、AuthRepository.observeIsAuthenticated() が
     * false に遷移する経路を介して
     * [com.feedman.android.core.auth.AuthRepositorySessionStateProvider] が
     * [com.feedman.android.core.auth.SessionState.LoggedOut] を流す
     * （Req 4.1 / 4.2 / 4.3 / 5.2）。
     */
    fun logout() {
        // 多重起動防止（Req 1.3 の追加保険）
        if (logoutJob?.isActive == true) return
        val current = _uiState.value
        if (current !is AccountSheetUiState.Visible) return
        // Issue #51 Req 3.3: 退会フロー中（ConfirmExplanation / ConfirmFinal / InProgress）は
        // ログアウト操作を受け付けない（UI 上も disabled 表示にする）
        if (current.deletion !is AccountSheetUiState.DeletionState.Idle &&
            current.deletion !is AccountSheetUiState.DeletionState.Error
        ) {
            return
        }

        // Req 1.3 / 1.4: 進行中状態に遷移
        _uiState.value = current.copy(logoutInProgress = true)
        // Req 3.3: ログアウト確定前に cachedUser を破棄する（次回 open でも再現しない）
        cachedUser = null
        // 取得進行中なら不要なので止める（観測可能状態が混ざるのを防ぐ）
        fetchJob?.cancel()
        fetchJob = null

        logoutJob = viewModelScope.launch {
            try {
                // Req 2.1: revoke + キャッシュリセット（NFR 1.2 上限 10 秒）。
                // coordinator は例外を投げない契約。
                logoutCoordinator.perform()
            } finally {
                // Req 4.3: シートを閉じる（SessionState 経路でログイン画面に遷移）
                _uiState.value = AccountSheetUiState.Hidden
            }
        }
    }

    /**
     * Req 4.2: 回復可能エラー時の再試行。Loading に遷移して取得を再実行する。
     *
     * Loaded / Loading / Hidden 状態では no-op（不正な呼び出しに対する防御）。
     */
    fun retry() {
        val current = _uiState.value as? AccountSheetUiState.Visible ?: return
        if (current.loadState !is AccountSheetUiState.LoadState.Error) return
        startFetch()
    }

    // ── Issue #51: 退会フロー ────────────────────────────────

    /**
     * Issue #51 Req 1.3 / 2.1 / 2.2: 退会操作を開始する（説明ダイアログを表示する）。
     *
     * 観測可能挙動:
     * - 現在 [AccountSheetUiState.Visible] であり、deletion が `Idle` または `Error` のときのみ
     *   `ConfirmExplanation` に遷移する（多重ダイアログを避ける）
     * - 進行中（`ConfirmExplanation` / `ConfirmFinal` / `InProgress`）からの再起動は no-op
     * - サーバーへの削除要求は送信しない（Req 1.4: 二段確認完了前は送らない）
     */
    fun startDeletion() {
        val current = _uiState.value as? AccountSheetUiState.Visible ?: return
        // ログアウト進行中は退会フローを起動しない（観測可能状態の競合を避ける）
        if (current.logoutInProgress) return
        when (current.deletion) {
            AccountSheetUiState.DeletionState.Idle,
            is AccountSheetUiState.DeletionState.Error -> {
                _uiState.value = current.copy(
                    deletion = AccountSheetUiState.DeletionState.ConfirmExplanation,
                )
            }
            AccountSheetUiState.DeletionState.ConfirmExplanation,
            AccountSheetUiState.DeletionState.ConfirmFinal,
            AccountSheetUiState.DeletionState.InProgress -> Unit
        }
    }

    /**
     * Issue #51 Req 2.3: 第 1 段「次へ進む」操作。
     *
     * 観測可能挙動: `ConfirmExplanation` 状態のときのみ `ConfirmFinal` に遷移する。
     * 他の状態（Idle / ConfirmFinal / InProgress / Error）では no-op（不正呼び出し防御）。
     * サーバーへの削除要求は送信しない（Req 1.4）。
     */
    fun proceedToFinalConfirm() {
        val current = _uiState.value as? AccountSheetUiState.Visible ?: return
        if (current.deletion !is AccountSheetUiState.DeletionState.ConfirmExplanation) return
        _uiState.value = current.copy(
            deletion = AccountSheetUiState.DeletionState.ConfirmFinal,
        )
    }

    /**
     * Issue #51 Req 2.5: 二段確認のいずれかでキャンセル操作（明示キャンセル / ダイアログ外タップ /
     * システム戻る）。
     *
     * 観測可能挙動:
     * - `ConfirmExplanation` / `ConfirmFinal` / `Error` のときは `Idle` に戻す
     *   （Req 2.5: アカウントシート表示状態へ戻す）
     * - `InProgress` のときは no-op（Req 3.2: 進行中はキャンセル不可。サーバー応答を待つ）
     * - サーバーへの削除要求は送信しない（Req 1.4 / 2.5）
     */
    fun cancelDeletion() {
        val current = _uiState.value as? AccountSheetUiState.Visible ?: return
        when (current.deletion) {
            AccountSheetUiState.DeletionState.ConfirmExplanation,
            AccountSheetUiState.DeletionState.ConfirmFinal,
            is AccountSheetUiState.DeletionState.Error -> {
                _uiState.value = current.copy(
                    deletion = AccountSheetUiState.DeletionState.Idle,
                )
            }
            AccountSheetUiState.DeletionState.InProgress,
            AccountSheetUiState.DeletionState.Idle -> Unit
        }
    }

    /**
     * Issue #51 Req 2.6 / 3.1 / 3.2 / 4 / 5: 第 2 段「退会を実行する」確定操作。
     *
     * 観測可能挙動:
     * 1. `ConfirmFinal` 状態のときのみ受け付ける（他は no-op）
     * 2. `InProgress` に遷移する（Req 3.1 / 3.2 / NFR 1.1: 1 秒以内にローディング表示）
     * 3. [AccountDeletionCoordinator.perform] を 1 回呼ぶ（Req 2.6）
     * 4. 成功 → cachedUser を破棄して Hidden に戻す。SessionState 遷移はトークン消去経由で
     *    AppShell が LoggedOut を描画する（Req 4.3 / 4.4 / 4.5）
     * 5. 失敗 → `DeletionState.Error(message)` に遷移し、ローカル状態を温存する
     *    （Req 5.1〜5.5）。ユーザーは再度 `startDeletion()` から二段確認をやり直せる
     *
     * 多重起動防止: 既に [deletionJob] が active な間は no-op（Req 3.2 の補強）。
     * 進行中の現在ユーザー取得ジョブ・ログアウトジョブは互いに矛盾するため本メソッド内で
     * は **起動しない**（既に Visible 状態で startDeletion 経路を踏んでいる前提）。
     */
    fun confirmDeletion() {
        // 多重起動防止
        if (deletionJob?.isActive == true) return
        val current = _uiState.value as? AccountSheetUiState.Visible ?: return
        if (current.deletion !is AccountSheetUiState.DeletionState.ConfirmFinal) return

        // Req 3.1 / 3.2 / NFR 1.1: 進行中状態に遷移
        _uiState.value = current.copy(
            deletion = AccountSheetUiState.DeletionState.InProgress,
        )

        deletionJob = viewModelScope.launch {
            // Req 2.6: DELETE /api/users/me を 1 回呼ぶ。Coordinator は例外を投げない契約。
            val result = accountDeletionCoordinator.perform()
            val mid = _uiState.value as? AccountSheetUiState.Visible
            when (result) {
                DeletionResult.Success -> {
                    // Req 4.3 / 4.5: cachedUser を破棄し Hidden に戻す。
                    // SessionState 遷移は TokenStore 消去経由で AppShell が描画切替する。
                    cachedUser = null
                    _uiState.value = AccountSheetUiState.Hidden
                }
                is DeletionResult.Failure -> {
                    // Req 5.1〜5.5: ローカル状態は温存。Error 表示に遷移。
                    // 現在 Visible 状態でなくなっていれば（close 等で）状態反映をスキップ。
                    if (mid != null) {
                        _uiState.value = mid.copy(
                            deletion = AccountSheetUiState.DeletionState.Error(result.message),
                        )
                    }
                }
            }
        }
    }

    /**
     * 取得を開始する。Visible(Loading) に遷移してから suspend 呼び出しを launch する。
     *
     * 例外分岐:
     * - [FeedmanException]（code = UNAUTHORIZED）: Hidden に戻し UnauthorizedRedirect を発火（Req 5.1〜5.3）
     * - [FeedmanException]（その他）: Visible(Error(message)) に遷移（Req 4.1 / 4.3）
     * - その他 [Exception]: Visible(Error(fallback message)) に遷移
     */
    private fun startFetch() {
        // 既存ジョブをキャンセル（多重実行抑止）
        fetchJob?.cancel()
        _uiState.value = AccountSheetUiState.Visible(
            loadState = AccountSheetUiState.LoadState.Loading,
        )
        fetchJob = viewModelScope.launch {
            try {
                val user = repository.getCurrentUser()
                cachedUser = user
                _uiState.value = AccountSheetUiState.Visible(
                    loadState = AccountSheetUiState.LoadState.Loaded(user),
                )
            } catch (e: FeedmanException) {
                if (e.code == CODE_UNAUTHORIZED) {
                    // Req 5.1: 認証エラー時はシートを閉じる
                    _uiState.value = AccountSheetUiState.Hidden
                    // Req 5.2 / 5.3: ログイン導線への遷移を SessionState 観測経路に委ねる
                    _events.emit(AccountSheetEvent.UnauthorizedRedirect)
                    return@launch
                }
                // Req 4.1: 回復可能エラー（NETWORK_ERROR / UNKNOWN_ERROR / 5xx 等）
                _uiState.value = AccountSheetUiState.Visible(
                    loadState = AccountSheetUiState.LoadState.Error(resolveMessage(e)),
                )
            } catch (e: Exception) {
                // 想定外例外（kotlinx.coroutines.CancellationException は coroutines が再 throw する）
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.value = AccountSheetUiState.Visible(
                    loadState = AccountSheetUiState.LoadState.Error(
                        e.message?.takeIf { it.isNotBlank() }
                            ?: FeedmanException.FALLBACK_UNKNOWN_MESSAGE,
                    ),
                )
            }
        }
    }

    /**
     * Req 4.1: サーバー応答のエラーメッセージ（空のときは code 別の汎用文言）。
     */
    private fun resolveMessage(e: FeedmanException): String =
        e.errorMessage.ifBlank {
            when (e.code) {
                FeedmanException.CODE_NETWORK_ERROR -> FeedmanException.FALLBACK_NETWORK_MESSAGE
                else -> FeedmanException.FALLBACK_UNKNOWN_MESSAGE
            }
        }

    private companion object {
        /** SPEC §4.3 認証切れ応答の code。 */
        const val CODE_UNAUTHORIZED: String = "UNAUTHORIZED"
    }
}
