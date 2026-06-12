package com.feedman.android.feature.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.feedman.android.core.data.CrossFeedRepository
import com.feedman.android.core.data.ItemRepository
import com.feedman.android.core.data.ItemStateOverlay
import com.feedman.android.core.data.ItemStateStore
import com.feedman.android.core.model.AppConfig
import com.feedman.android.core.ui.ArticleCardModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 横断タイムライン画面の ViewModel（Issue #33 / Issue #38）。
 *
 * 横断新着タイムラインを `Flow<PagingData<ArticleCardModel>>` として公開する。Paging 3 の
 * `cachedIn(viewModelScope)` でセッション中はキャッシュを保持し、再コンポジション時に
 * 再ロードが走らないようにする（NFR 2.1）。
 *
 * ## Issue #38 — ItemStateStore 連携（Req 1.1 / 3.1〜3.4 / 4.4 / 5.2）
 *
 * 既読 / スターの楽観的更新は [ItemStateStore] が単一データ点として保持する。本 VM は:
 *
 * - `cachedIn` 後の `PagingData<ArticleCardModel>` と `store.overlays` を `combine` し、
 *   overlay 値をサーバー由来値より優先して各カードの isRead / isStarred を解決する（Req 3.1）。
 * - 外部リンク起動成功後の既読化を `store.markRead` 経由で発火（Req 5.2）。失敗時 snackbar は
 *   store の `failures` 経由で発火する（Req 2.3）— 本 VM が再送する SharedFlow は
 *   外部リンク自体の起動失敗（OpenLinkFailed）通知のみに役割を絞る。
 * - スタートグルは [toggleStar] が `store.setStarred` を呼び出し、楽観的反映とサーバー反映
 *   失敗時のロールバックを store に委譲する（Req 1.1 / 4.4）。
 *
 * ## mockMode 分岐
 *
 * `AppConfig.mockMode == true` のときは [ItemRepository] のモックスナップショットを
 * `PagingData.from(...)` で 1 ページに包んで流す（Paging 3 のテスト用ファクトリ）。
 *
 * ## 公開 API
 *
 * - [cardPagingData]: 画面が `collectAsLazyPagingItems()` で購読する `Flow<PagingData<ArticleCardModel>>`
 *   （overlay 合成済み）
 * - [externalLinkEvents]: 外部リンク **起動自体**の失敗通知（既読化失敗は store.failures に統一）
 * - [toggleStar]: スタートグル
 * - [markReadOnExternalOpen]: 外部リンク起動成功後の既読化
 */
@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val crossFeedRepository: CrossFeedRepository,
    private val itemRepository: ItemRepository,
    private val appConfig: AppConfig,
    private val itemStateStore: ItemStateStore,
) : ViewModel() {

    // Issue #37 Req 4.x: 外部リンクの起動自体の失敗（InvalidUrl / NoAppToHandle）通知のみを
    // 本 VM の SharedFlow で扱う。既読化失敗は ItemStateStore.failures に統一されている
    // ため、UI 側はそちらを別途購読する。
    private val _externalLinkEvents = MutableSharedFlow<TimelineExternalLinkEvent>(
        replay = 0,
        extraBufferCapacity = 4,
    )
    val externalLinkEvents: SharedFlow<TimelineExternalLinkEvent> = _externalLinkEvents.asSharedFlow()

    /**
     * Issue #38 Req 2.3 経由の購読向けに `ItemStateStore.failures` を再公開する。
     * UI 側は本 VM のみを観測すれば良い構造を維持する。
     */
    val itemStateFailures: SharedFlow<com.feedman.android.core.data.ItemStateFailure> =
        itemStateStore.failures

    /**
     * 外部リンク起動成功時の既読化（Issue #37 Req 2.2 / Issue #38 Req 5.2）。
     *
     * [ItemStateStore.markRead] が冪等性（既読なら no-op）とサーバー反映、失敗時 snackbar 通知
     * （`failures`）までを担う。
     *
     * @param itemId 外部リンクを開いた記事の ID
     * @param currentIsRead overlay 合成後の現在の既読状態（カード描画から渡す）
     */
    fun markReadOnExternalOpen(itemId: String, currentIsRead: Boolean) {
        itemStateStore.markRead(itemId = itemId, currentIsRead = currentIsRead)
    }

    /**
     * カードのスタートグル（Issue #38 Req 1.1 / 4.4）。
     *
     * [ItemStateStore.setStarred] が楽観的反映・サーバー反映・失敗時ロールバック・通知までを担う。
     *
     * @param itemId 対象記事 ID
     * @param newState トグル後の新しいスター状態（!current）
     * @param baselineStarred ロールバック先（合成前のサーバー値 or 直前の overlay 値）
     */
    fun toggleStar(itemId: String, newState: Boolean, baselineStarred: Boolean) {
        itemStateStore.setStarred(
            itemId = itemId,
            isStarred = newState,
            baselineStarred = baselineStarred,
        )
    }

    /**
     * 外部リンク起動失敗時の通知（Issue #37 Req 2.4 / Req 3.3 / Req 4.1 / Req 4.2）。
     *
     * URL 不正（InvalidUrl）や対応アプリ不在（NoAppToHandle）の通知を UI へ流すための API。
     * 既読化は走らない（Req 4.3）。
     */
    fun notifyExternalLinkFailed() {
        viewModelScope.launch {
            _externalLinkEvents.emit(TimelineExternalLinkEvent.OpenLinkFailed)
        }
    }

    /**
     * カード描画用 PagingData の Flow。Composable は `collectAsLazyPagingItems()` で購読する。
     *
     * `cachedIn(viewModelScope)` 後に `overlays` と `combine` し、各カードの isRead / isStarred を
     * overlay 値で上書きしてから配信する（Issue #38 Req 3.1〜3.4）。
     */
    val cardPagingData: Flow<PagingData<ArticleCardModel>> =
        buildBaseCardPagingData()
            .cachedIn(viewModelScope)
            .combineWithOverlays(itemStateStore.overlays)

    /**
     * テスト用に公開する変換ロジック本体。`cachedIn(viewModelScope)` 適用前の生 Flow を
     * overlay と合成したものを返す。長寿命 collector が残らないため `runTest` 上で
     * `asSnapshot()` を呼べる。
     */
    internal fun cardPagingDataForTest(): Flow<PagingData<ArticleCardModel>> {
        // `combine(stateFlow)` は StateFlow が完了しないため `asSnapshot()` が
        // テスト終了時に `UncompletedCoroutinesError` を起こす。`take(1)` で 1 つ目の
        // overlay スナップショットを取り出した時点で完了させる（合成結果の検証には
        // 1 回の値で十分。`overlays` は StateFlow なので必ず即座に値を持っている）。
        return buildBaseCardPagingData().combineWithOverlays(itemStateStore.overlays.take(1))
    }

    private fun buildBaseCardPagingData(): Flow<PagingData<ArticleCardModel>> {
        return if (appConfig.mockMode) {
            itemRepository.observeTimeline().map { mockItems ->
                val cards = mockItems.map { mockItem ->
                    TimelineCardModelMapper.toMockCardModel(
                        item = mockItem,
                        fallbackPublishedAtIso = MOCK_FALLBACK_PUBLISHED_AT_ISO,
                    )
                }
                PagingData.from(cards)
            }
        } else {
            crossFeedRepository.pagingData().map { paging ->
                paging.map { TimelineCardModelMapper.toCardModel(it) }
            }
        }
    }

    private companion object {
        const val MOCK_FALLBACK_PUBLISHED_AT_ISO: String = "2026-06-12T11:30:00Z"
    }
}

/**
 * `PagingData<ArticleCardModel>` の Flow と overlay の Flow を `combine` し、
 * overlay 値で各カードの isRead / isStarred を上書きする（Issue #38 Req 3.1〜3.4）。
 *
 * `cachedIn(viewModelScope)` の **後** に呼ばれることを想定する。生 Flow に対して呼んだ場合
 * テスト用途では問題ないが、本番経路では `cachedIn` の前に置くと購読ごとに Pager が
 * 再生成されてしまうため避ける。
 */
private fun Flow<PagingData<ArticleCardModel>>.combineWithOverlays(
    overlays: Flow<Map<String, ItemStateOverlay>>,
): Flow<PagingData<ArticleCardModel>> =
    combine(overlays) { paging, currentOverlays ->
        paging.map { card ->
            val o = currentOverlays[card.id] ?: return@map card
            card.copy(
                isRead = o.isRead ?: card.isRead,
                isStarred = o.isStarred ?: card.isStarred,
            )
        }
    }
