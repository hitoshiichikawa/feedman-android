package com.feedman.android.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
     * Req 4.2: 回復可能エラー時の再試行。Loading に遷移して取得を再実行する。
     *
     * Loaded / Loading / Hidden 状態では no-op（不正な呼び出しに対する防御）。
     */
    fun retry() {
        val current = _uiState.value as? AccountSheetUiState.Visible ?: return
        if (current.loadState !is AccountSheetUiState.LoadState.Error) return
        startFetch()
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
