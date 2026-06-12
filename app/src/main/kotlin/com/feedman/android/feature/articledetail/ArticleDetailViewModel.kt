package com.feedman.android.feature.articledetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feedman.android.core.data.ItemDetailRepository
import com.feedman.android.core.network.FeedmanException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 記事詳細シートの ViewModel（Issue #36 / Req 1, 3, 4, 5, 6 / NFR 1.1）。
 *
 * シート起動 → 詳細取得 → 自動既読化 → 表示 という直線的なフローを扱い、
 * 既読化 / スター更新の楽観的更新と失敗時ロールバックを担当する。シート単体に閉じる範囲のみ
 * を扱い、画面横断同期（呼び出し元一覧の行への反映）は Issue #38 に切り出している
 * （requirements.md "Out of Scope" 参照）。
 *
 * ## 状態遷移
 *
 * ```
 * Hidden ──open(id)──▶ Loading ──成功──▶ Content（即時既読化を内部で発火）
 *                              └──失敗──▶ Error ──retry──▶ Loading
 * Content ──toggleStar──▶ Content（楽観更新 → 失敗時ロールバック + event 発火）
 * 任意 ──dismiss──▶ Hidden
 * ```
 *
 * ## 楽観的更新の規約
 *
 * - **既読化（Req 3）**: シート Composable が表示された時点（Content への遷移時）に
 *   `isRead=true` を内部状態に即時反映 + サーバー API を呼ぶ。元が既読のときは API を
 *   呼ばない（Req 3.5 — 冪等）。失敗時は内部状態を `isRead=false` に巻き戻し
 *   [events] で [ArticleDetailEvent.MarkReadFailed] を流す（Req 3.3）。
 * - **スター（Req 4）**: [toggleStar] が呼ばれた瞬間に内部状態をトグル + サーバー API を呼ぶ。
 *   失敗時はトグル前の値に巻き戻し [events] で [ArticleDetailEvent.StarUpdateFailed] を流す
 *   （Req 4.5）。本文上部とフッタのスター表示は同じ `Content.isStarred` を参照することで
 *   常に整合する（Req 4.6 / Req 5.3）。
 *
 * ## 「元記事を開く」既読化
 *
 * [markReadOnOpenExternal] は Req 4.3 に対応。未読のときのみ既読化 API を呼ぶ。シート起動時の
 * 既読化（Req 3.1）と同じパスを通るが、既に既読であれば即座に no-op として返す（Req 4.3）。
 *
 * ## DI
 *
 * `RepositoryModule` でバインド済みの [ItemDetailRepository] を Hilt から注入する。
 * 単体テストでは fake repository を直接コンストラクタへ渡す（NFR 1.1）。
 */
@HiltViewModel
class ArticleDetailViewModel @Inject constructor(
    private val repository: ItemDetailRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ArticleDetailUiState>(ArticleDetailUiState.Hidden)
    val uiState: StateFlow<ArticleDetailUiState> = _uiState.asStateFlow()

    // replay = 0: 一度だけ届く one-shot 通知。再コンポジションでは再送しない（Req 3.3 / 4.5）。
    private val _events = MutableSharedFlow<ArticleDetailEvent>(replay = 0, extraBufferCapacity = 4)
    val events: SharedFlow<ArticleDetailEvent> = _events.asSharedFlow()

    /**
     * シートを開く（Req 1.1）。
     *
     * `itemId` で `getItem(id)` を発行し、成功時に [ArticleDetailUiState.Content] へ遷移、
     * 同時にシート起動時の自動既読化（Req 3.1, 3.2）を発火する。
     *
     * 既に同じ ID で Loading / Content / Error のいずれかにある状態で再呼び出しされても、
     * **常に再取得から始める**（明示的に閉じる前提のため重複起動は起きないが、保険として）。
     */
    fun open(itemId: String) {
        _uiState.value = ArticleDetailUiState.Loading(itemId = itemId)
        viewModelScope.launch {
            fetchAndApply(itemId = itemId)
        }
    }

    /**
     * シートを閉じる（Req 1.4, 1.5）。状態を [ArticleDetailUiState.Hidden] に戻す。
     */
    fun dismiss() {
        _uiState.value = ArticleDetailUiState.Hidden
    }

    /**
     * 取得失敗 → 再試行（Req 6.3）。現在の Error 状態の `itemId` で再度 [open] を行う。
     */
    fun retry() {
        val current = _uiState.value
        if (current is ArticleDetailUiState.Error) {
            open(itemId = current.itemId)
        }
    }

    /**
     * フッタ / 本文上部スタートグル（Req 4.4, 4.5, 4.6）。
     *
     * Content 状態でのみ動作。即時 UI 反映 → サーバー API → 失敗ロールバック + イベント。
     */
    fun toggleStar() {
        val current = _uiState.value as? ArticleDetailUiState.Content ?: return
        val previous = current.isStarred
        val next = !previous
        _uiState.value = current.copy(isStarred = next)

        viewModelScope.launch {
            try {
                repository.updateState(itemId = current.detail.id, isRead = null, isStarred = next)
            } catch (e: FeedmanException) {
                // ロールバック対象が依然として同じ記事の Content であることを保証
                rollbackStarIfStillSameContent(itemId = current.detail.id, previous = previous)
                _events.emit(ArticleDetailEvent.StarUpdateFailed)
            }
        }
    }

    /**
     * 「元記事を開く」タップ時の既読化（Req 4.3）。
     *
     * 未読の場合のみ既読化 API を発火する（冪等）。シート起動時の既読化（Req 3.1）と
     * 同じパスを通る。
     */
    fun markReadOnOpenExternal() {
        val current = _uiState.value as? ArticleDetailUiState.Content ?: return
        if (current.isRead) return
        applyOptimisticRead(itemId = current.detail.id, current = current)
    }

    // ── 内部処理 ─────────────────────────────────────────────────────────

    private suspend fun fetchAndApply(itemId: String) {
        val detail = try {
            repository.getItem(itemId = itemId)
        } catch (e: FeedmanException) {
            // Req 6.2: 取得失敗をエラー状態として保持
            _uiState.value = ArticleDetailUiState.Error(itemId = itemId, message = e.errorMessage)
            return
        }

        // 取得成功 → Content へ遷移（Req 1.1）。
        // Req 3.1: シート表示時に楽観的既読化を即時反映。元が既読のときは API を呼ばない
        // （Req 3.5 — 冪等）。
        val initialContent = ArticleDetailUiState.Content(
            detail = detail,
            isRead = true, // Req 3.1, 3.4 — 即時に既読として描画
            isStarred = detail.isStarred,
        )
        _uiState.value = initialContent

        if (!detail.isRead) {
            // Req 3.2: サーバーに既読更新リクエストを送出
            try {
                repository.updateState(itemId = detail.id, isRead = true, isStarred = null)
            } catch (e: FeedmanException) {
                // Req 3.3: ロールバック + 通知。同じ Content がまだ表示中の場合のみロールバック。
                rollbackReadIfStillSameContent(itemId = detail.id)
                _events.emit(ArticleDetailEvent.MarkReadFailed)
            }
        }
        // else: Req 3.5 — 既読のまま再送しない
    }

    private fun applyOptimisticRead(itemId: String, current: ArticleDetailUiState.Content) {
        _uiState.value = current.copy(isRead = true)
        viewModelScope.launch {
            try {
                repository.updateState(itemId = itemId, isRead = true, isStarred = null)
            } catch (e: FeedmanException) {
                rollbackReadIfStillSameContent(itemId = itemId)
                _events.emit(ArticleDetailEvent.MarkReadFailed)
            }
        }
    }

    private fun rollbackReadIfStillSameContent(itemId: String) {
        val s = _uiState.value
        if (s is ArticleDetailUiState.Content && s.detail.id == itemId) {
            _uiState.value = s.copy(isRead = false)
        }
    }

    private fun rollbackStarIfStillSameContent(itemId: String, previous: Boolean) {
        val s = _uiState.value
        if (s is ArticleDetailUiState.Content && s.detail.id == itemId) {
            _uiState.value = s.copy(isStarred = previous)
        }
    }
}
