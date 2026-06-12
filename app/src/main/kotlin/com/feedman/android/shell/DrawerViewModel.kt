package com.feedman.android.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feedman.android.core.data.SubscriptionLoadState
import com.feedman.android.core.data.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ドロワーのフィード一覧 UI 状態を保持する ViewModel
 * （Issue #30 + Issue #39 / Req 1.1, 1.3, 1.5, 2.1, 2.5, 5.3, NFR 2.1, NFR 3.1, NFR 3.2）。
 *
 * [SubscriptionRepository] を観測して [DrawerUiState] を `StateFlow` で公開する。
 * #39 でフィード取得状態（Loading / Error）を UiState に統合し、UI 側でフィード
 * セクション内のエラー + 再試行表示を駆動できるようにした（Req 2.1, 2.2, 2.5）。
 *
 * - Req 1.1: ドロワーオープン時にリストを取得して表示（StateFlow 経由で読み取り可能）
 * - Req 1.5: リポジトリが返した順序をそのまま保持（map で要素変換のみ実施し並び替えない）
 * - NFR 2.1: 新しいフィードリストが流れたら次の再描画で自動反映（StateFlow 標準動作）
 * - NFR 3.1: [SubscriptionRepository] 抽象に依存するため、テストで stub に差し替え可能
 * - Issue #39 Req 2.4: 起動時およびユーザー再試行操作で `refresh()` を駆動する
 */
@HiltViewModel
class DrawerViewModel @Inject constructor(
    private val repository: SubscriptionRepository,
) : ViewModel() {

    val uiState: StateFlow<DrawerUiState> = combine(
        repository.observeSubscriptions(),
        repository.observeLoadState(),
    ) { subscriptions, loadState ->
        DrawerUiState(
            rows = subscriptions.map(DrawerFeedRow::from),
            feedSection = FeedSectionState.from(loadState, hasRows = subscriptions.isNotEmpty()),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = DrawerUiState(),
    )

    init {
        // Issue #39 Req 1.1, 2.4: 初期取得を起動する。Fake では no-op。
        viewModelScope.launch { repository.refresh() }
    }

    /**
     * フィードセクションのエラー表示で「再試行」がタップされたときの起点（Req 2.4）。
     * Repository 側で in-flight が直列化されるため、連打しても 1 in-flight に収束する。
     */
    fun retryLoadSubscriptions() {
        viewModelScope.launch { repository.refresh() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/**
 * ドロワーのフィード一覧 UI 状態（Issue #30 + Issue #39 / Req 1.3, 2.1, 2.2, 2.5）。
 *
 * @property rows ドロワーに表示するフィード行。空のときはフィード行を 1 件も描画しない（Req 1.3）。
 * @property feedSection フィードセクション領域の状態（Idle / Loading / Success / Error）。
 *   フィード行リストとは独立に保持される（取得失敗中でも直近の rows を引き続き表示できる）。
 */
data class DrawerUiState(
    val rows: List<DrawerFeedRow> = emptyList(),
    val feedSection: FeedSectionState = FeedSectionState.Idle,
)

/**
 * ドロワーのフィードセクション領域の UI 状態（Issue #39 / Req 2.1, 2.2, 2.5）。
 *
 * `SubscriptionLoadState` を UI 表示判定に正規化したビュー。`Loading` は初回ロード中のみ
 * フィードセクションにロード表示を出し、既に rows がある場合のリフレッシュは silent
 * とする（UI ちらつき抑制）。
 */
sealed interface FeedSectionState {

    /** 初期状態。まだ取得を試みていない。何も表示しない。 */
    data object Idle : FeedSectionState

    /** 初回ロード中（rows が空の状態での取得中）。フィードセクションにロード表示を出す。 */
    data object Loading : FeedSectionState

    /** 取得成功。通常のフィード行リスト表示。 */
    data object Success : FeedSectionState

    /**
     * 取得失敗。フィードセクション内にエラー文言と再試行操作を提示する（Req 2.1, 2.2, 2.6）。
     * `message` はサーバー由来 / フォールバック由来のユーザー向け文言。
     */
    data class Error(val message: String) : FeedSectionState

    companion object {
        /**
         * [SubscriptionLoadState] と現在の rows 有無から UI 表示状態を導出する。
         *
         * - 取得失敗 → 常に Error（rows の有無に関わらず再試行表示を出す / Req 2.2）。
         * - 取得中 + rows 空 → Loading（初回ロード）。
         * - 取得中 + rows あり → Success（silent refresh: 既存 rows を引き続き表示）。
         * - 取得成功 → Success。
         * - 初期 Idle → Idle。
         */
        fun from(loadState: SubscriptionLoadState, hasRows: Boolean): FeedSectionState =
            when (loadState) {
                SubscriptionLoadState.Idle -> Idle
                SubscriptionLoadState.Loading -> if (hasRows) Success else Loading
                SubscriptionLoadState.Success -> Success
                is SubscriptionLoadState.Error -> Error(message = loadState.message)
            }
    }
}
