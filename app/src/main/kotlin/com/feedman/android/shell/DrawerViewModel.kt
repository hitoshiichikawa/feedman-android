package com.feedman.android.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feedman.android.core.data.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ドロワーのフィード一覧 UI 状態を保持する ViewModel（Issue #30 / Req 1, 5, NFR 2.1, NFR 3.1）。
 *
 * [SubscriptionRepository] を観測して [DrawerUiState] を `StateFlow` で公開する。
 *
 * - Req 1.1: ドロワーオープン時にリストを取得して表示（StateFlow 経由で読み取り可能）
 * - Req 1.5: リポジトリが返した順序をそのまま保持（map で要素変換のみ実施し並び替えない）
 * - NFR 2.1: 新しいフィードリストが流れたら次の再描画で自動反映（StateFlow 標準動作）
 * - NFR 3.1: [SubscriptionRepository] 抽象に依存するため、テストで stub に差し替え可能
 */
@HiltViewModel
class DrawerViewModel @Inject constructor(
    repository: SubscriptionRepository,
) : ViewModel() {

    val uiState: StateFlow<DrawerUiState> = repository.observeSubscriptions()
        .map { subscriptions -> DrawerUiState(rows = subscriptions.map(DrawerFeedRow::from)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = DrawerUiState(),
        )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/**
 * ドロワーのフィード一覧 UI 状態（Issue #30 / Req 1.3）。
 *
 * @property rows ドロワーに表示するフィード行。空のときはフィード行を 1 件も描画しない（Req 1.3）。
 */
data class DrawerUiState(
    val rows: List<DrawerFeedRow> = emptyList(),
)
