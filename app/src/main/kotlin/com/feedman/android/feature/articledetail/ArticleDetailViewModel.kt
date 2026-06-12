package com.feedman.android.feature.articledetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feedman.android.core.data.ItemDetailRepository
import com.feedman.android.core.data.ItemStateFailure
import com.feedman.android.core.data.ItemStateStore
import com.feedman.android.core.model.ItemDetail
import com.feedman.android.core.network.FeedmanException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 記事詳細シートの ViewModel（Issue #36 / Issue #38）。
 *
 * シート起動 → 詳細取得 → 自動既読化 → 表示 という直線的なフローを扱い、既読化 / スター更新の
 * 楽観的更新と失敗時通知は [ItemStateStore] に集約する（Issue #38 Req 4.3 / 4.4）。
 *
 * ## 状態遷移
 *
 * ```
 * Hidden ──open(id)──▶ Loading ──成功──▶ Content（即時既読化を内部で発火）
 *                              └──失敗──▶ Error ──retry──▶ Loading
 * Content ──toggleStar──▶ Content（store 経由で楽観更新 → 失敗時は store がロールバック）
 * 任意 ──dismiss──▶ Hidden
 * ```
 *
 * ## 楽観的更新の規約（#38 連携）
 *
 * - **既読化（Req 3 / Issue #38 Req 5.1）**: シート Composable が表示された時点
 *   （Content への遷移時）に `store.markRead(itemId, currentIsRead)` を呼ぶ。`currentIsRead`
 *   はサーバー値ベース。store は冪等性とサーバー反映・失敗イベント発行を担う。
 * - **スター（Req 4 / Issue #38 Req 1.1）**: [toggleStar] が `store.setStarred` を呼ぶ。
 * - **Content の表示値（isRead / isStarred）**: サーバー由来値（[ItemDetail]）と
 *   `store.overlays` を combine して合成する。ある画面で行ったトグル結果が他画面に反映される
 *   経路もこの購読で実現する（Issue #38 Req 4.2 / 4.3）。
 * - **失敗通知**: 後方互換性のため [events] は維持するが、内部実装は store.failures を
 *   購読してリレーする。
 *
 * ## 「元記事を開く」既読化
 *
 * [markReadOnOpenExternal] は store の markRead に委譲する（冪等）。
 *
 * ## DI
 *
 * `RepositoryModule` でバインド済みの [ItemDetailRepository] と Singleton の [ItemStateStore]
 * を Hilt から注入する。単体テストでは fake repository と fresh store を直接渡す。
 */
@HiltViewModel
class ArticleDetailViewModel @Inject constructor(
    private val repository: ItemDetailRepository,
    private val itemStateStore: ItemStateStore,
) : ViewModel() {

    // 詳細取得結果と loading/error を保持する内部 raw state。
    // 公開する uiState は store.overlays と combine してから露出する。
    private val _rawState = MutableStateFlow<RawState>(RawState.Hidden)

    val uiState: StateFlow<ArticleDetailUiState> =
        combine(_rawState, itemStateStore.overlays) { raw, overlays ->
            raw.toUiState(overlays)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ArticleDetailUiState.Hidden,
        )

    private val _events = MutableSharedFlow<ArticleDetailEvent>(replay = 0, extraBufferCapacity = 4)

    /**
     * 既読 / スター更新失敗の one-shot 通知。実体は [ItemStateStore.failures] のリレー。
     * 後方互換性のため `MarkReadFailed` / `StarUpdateFailed` の sealed class を維持する。
     */
    val events: SharedFlow<ArticleDetailEvent> = _events.asSharedFlow()

    init {
        // Issue #38 Req 2.3: store.failures を購読し、当 ViewModel の events に変換する。
        // 詳細シートが見ている item の失敗だけでなく全体の failure を受けるため、UI 側で
        // 必要に応じてフィルタする（v1 では現状の Content.itemId に紐づくものだけを反応させる）。
        viewModelScope.launch {
            itemStateStore.failures.collect { failure ->
                val raw = _rawState.value
                if (raw is RawState.Content && raw.detail.id == failure.itemId) {
                    val event = when (failure.kind) {
                        ItemStateFailure.Kind.Read -> ArticleDetailEvent.MarkReadFailed
                        ItemStateFailure.Kind.Star -> ArticleDetailEvent.StarUpdateFailed
                    }
                    _events.emit(event)
                }
            }
        }
    }

    /**
     * シートを開く（Req 1.1）。
     *
     * `itemId` で `getItem(id)` を発行し、成功時に Content へ遷移、同時にシート起動時の
     * 自動既読化（Req 3.1, 3.2 / Issue #38 Req 5.1）を `store.markRead` 経由で発火する。
     */
    fun open(itemId: String) {
        _rawState.value = RawState.Loading(itemId = itemId)
        viewModelScope.launch {
            fetchAndApply(itemId = itemId)
        }
    }

    /** シートを閉じる（Req 1.4, 1.5）。状態を [ArticleDetailUiState.Hidden] に戻す。 */
    fun dismiss() {
        _rawState.value = RawState.Hidden
    }

    /** 取得失敗 → 再試行（Req 6.3）。現在の Error 状態の `itemId` で再度 [open] を行う。 */
    fun retry() {
        val current = _rawState.value
        if (current is RawState.Error) {
            open(itemId = current.itemId)
        }
    }

    /**
     * フッタ / 本文上部スタートグル（Req 4.4, 4.5, 4.6 / Issue #38 Req 1.1）。
     *
     * Content 状態でのみ動作。`uiState` の最新の表示値（overlay 合成済み）を起点に新値を計算し、
     * store に委譲する。
     */
    fun toggleStar() {
        val raw = _rawState.value as? RawState.Content ?: return
        val current = uiState.value as? ArticleDetailUiState.Content ?: return
        val previous = current.isStarred
        val next = !previous
        itemStateStore.setStarred(
            itemId = raw.detail.id,
            isStarred = next,
            baselineStarred = previous,
        )
    }

    /**
     * 「元記事を開く」タップ時の既読化（Req 4.3 / Issue #38 Req 5.2）。
     *
     * 未読の場合のみ既読化 API を発火する（store.markRead が冪等性を担う）。
     */
    fun markReadOnOpenExternal() {
        val current = uiState.value as? ArticleDetailUiState.Content ?: return
        itemStateStore.markRead(itemId = current.detail.id, currentIsRead = current.isRead)
    }

    // ── 内部処理 ─────────────────────────────────────────────────────────

    private suspend fun fetchAndApply(itemId: String) {
        val detail = try {
            repository.getItem(itemId = itemId)
        } catch (e: FeedmanException) {
            // Req 6.2: 取得失敗をエラー状態として保持
            _rawState.value = RawState.Error(itemId = itemId, message = e.errorMessage)
            return
        }

        // 取得成功 → Content へ遷移（Req 1.1）。
        _rawState.value = RawState.Content(detail = detail)

        // Req 3.1 / 3.2 / Issue #38 Req 5.1: シート表示時に既読化トリガーを発火。
        // store が冪等性（detail.isRead=true なら no-op）・サーバー反映・失敗通知までを担う。
        itemStateStore.markRead(itemId = detail.id, currentIsRead = detail.isRead)
    }

    // ── 内部 raw state（overlay 合成前） ────────────────────────────────

    /**
     * overlay 合成前の素のシート状態。Loading / Error / Hidden は overlay と無関係なので
     * そのまま外部 [ArticleDetailUiState] に対応する。Content だけは store.overlays を
     * 適用した値で [ArticleDetailUiState.Content] に変換する。
     */
    private sealed class RawState {
        data object Hidden : RawState()
        data class Loading(val itemId: String) : RawState()
        data class Content(val detail: ItemDetail) : RawState()
        data class Error(val itemId: String, val message: String) : RawState()

        fun toUiState(
            overlays: Map<String, com.feedman.android.core.data.ItemStateOverlay>,
        ): ArticleDetailUiState = when (this) {
            is Hidden -> ArticleDetailUiState.Hidden
            is Loading -> ArticleDetailUiState.Loading(itemId = itemId)
            is Error -> ArticleDetailUiState.Error(itemId = itemId, message = message)
            is Content -> {
                val overlay = overlays[detail.id]
                ArticleDetailUiState.Content(
                    detail = detail,
                    isRead = overlay?.isRead ?: detail.isRead,
                    isStarred = overlay?.isStarred ?: detail.isStarred,
                )
            }
        }
    }
}
