package com.feedman.android.feature.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.feedman.android.core.data.CrossFeedRepository
import com.feedman.android.core.data.ItemDetailRepository
import com.feedman.android.core.data.ItemRepository
import com.feedman.android.core.model.AppConfig
import com.feedman.android.core.network.FeedmanException
import com.feedman.android.core.ui.ArticleCardModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 横断タイムライン画面の ViewModel（Issue #33 / Req 5.1〜5.4 / NFR 2.1）。
 *
 * 横断新着タイムラインを `Flow<PagingData<ArticleCardModel>>` として公開する。Paging 3 の
 * `cachedIn(viewModelScope)` でセッション中はキャッシュを保持し、再コンポジション時に
 * 再ロードが走らないようにする（NFR 2.1）。
 *
 * ## mockMode 分岐
 *
 * `AppConfig.mockMode == true` のときは [ItemRepository] のモックスナップショットを
 * `PagingData.from(...)` で 1 ページに包んで流す（Paging 3 のテスト用ファクトリ）。
 * これは API 接続不要な開発時起動（`-Pfeedman.mockMode=true`）で TimelineScreen を即時
 * 表示するための便宜機能で、実機の挙動とは別系統である。
 *
 * ## 公開 API
 *
 * - [cardPagingData]: 画面が `collectAsLazyPagingItems()` で購読する `Flow<PagingData<ArticleCardModel>>`
 *
 * 共有 ArticleCard 部品（Issue #27 / #33）への変換は [TimelineCardModelMapper] に委譲する。
 */
@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val crossFeedRepository: CrossFeedRepository,
    private val itemRepository: ItemRepository,
    private val appConfig: AppConfig,
    /**
     * Issue #37: タイムラインカードの外部リンクアイコンタップで Custom Tabs を開いた後、
     * 当該記事を既読化（`is_read:true`）するために `ItemDetailRepository.updateState` を呼ぶ。
     * 失敗時は [externalLinkEvents] に [TimelineExternalLinkEvent.MarkReadFailed] を流して
     * UI 側で snackbar 通知する（Req 2.4）。画面間表示同期は #38 のスコープのため、ここでは
     * タイムライン側のカード表示更新は次の refresh 任せにする（impl-notes 参照）。
     */
    private val itemDetailRepository: ItemDetailRepository,
) : ViewModel() {

    // Issue #37 Req 2.4: 既読化失敗時の one-shot 通知（replay=0, buffer=4）。
    // UI 側は collect して snackbar を表示する。
    private val _externalLinkEvents = MutableSharedFlow<TimelineExternalLinkEvent>(
        replay = 0,
        extraBufferCapacity = 4,
    )
    val externalLinkEvents: SharedFlow<TimelineExternalLinkEvent> = _externalLinkEvents.asSharedFlow()

    /**
     * 外部リンク起動成功時の既読化（Issue #37 Req 2.2 / 2.4）。
     *
     * `is_read:true` のサーバー反映に失敗した場合は [TimelineExternalLinkEvent.MarkReadFailed]
     * を流す（既読のロールバックは行わない — タイムライン側は楽観的な内部 mutable 状態を
     * 保持していないため。画面間表示同期は #38 のスコープ）。
     *
     * @param itemId 外部リンクを開いた記事の ID
     */
    fun markReadOnExternalOpen(itemId: String) {
        viewModelScope.launch {
            try {
                itemDetailRepository.updateState(itemId = itemId, isRead = true, isStarred = null)
            } catch (e: FeedmanException) {
                _externalLinkEvents.emit(TimelineExternalLinkEvent.MarkReadFailed)
            }
        }
    }

    /**
     * 外部リンク起動失敗時の通知（Issue #37 Req 2.4 / Req 3.3 / Req 4.1 / Req 4.2）。
     *
     * URL 不正（InvalidUrl）や対応アプリ不在（NoAppToHandle）の通知を UI へ流すための API。
     * 既読化は走らない（Req 4.3）。
     */
    fun notifyExternalLinkFailed() {
        // SharedFlow.tryEmit は replay=0 / buffer 余裕がある間は同期的に成功するが、念のため
        // viewModelScope で emit を行うことでバッファ枯渇時の待ち合わせも吸収する。
        viewModelScope.launch {
            _externalLinkEvents.emit(TimelineExternalLinkEvent.OpenLinkFailed)
        }
    }

    /**
     * カード描画用 PagingData の Flow。Composable は `collectAsLazyPagingItems()` で購読する。
     *
     * - mockMode=false: `CrossFeedRepository.pagingData()` を `ArticleCardModel` に map
     * - mockMode=true: `ItemRepository.observeTimeline()` の最新スナップショットを
     *   `PagingData.from(...)` に詰めて 1 ページ相当を流す（開発時起動の便宜）
     *
     * いずれの分岐でも `cachedIn(viewModelScope)` でキャッシュし、画面回転や再コンポジション
     * 時の再ロードを防止する（NFR 2.1）。
     */
    val cardPagingData: Flow<PagingData<ArticleCardModel>> = buildCardPagingData()
        .cachedIn(viewModelScope)

    /**
     * テスト用に公開する変換ロジック本体。`cachedIn(viewModelScope)` 適用前の生 Flow を
     * 返すため、JVM 単体テストで `asSnapshot()` を呼んでも viewModelScope の長寿命 collector
     * が残らない（`runTest` が `UncompletedCoroutinesError` で失敗しなくなる）。
     *
     * Composable からは直接呼ばず、必ず [cardPagingData] を経由する（キャッシュ整合のため）。
     */
    internal fun cardPagingDataForTest(): Flow<PagingData<ArticleCardModel>> = buildCardPagingData()

    private fun buildCardPagingData(): Flow<PagingData<ArticleCardModel>> {
        return if (appConfig.mockMode) {
            // mockMode: MockTimelineItem のスナップショットを 1 ページとして流す。
            // `PagingData.from(...)` は静的データ用のテスト・モック向けファクトリ（公式 API）。
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
            // 実 API モード: CrossFeedRepository が返す PagingData<CrossFeedItem> を
            // ArticleCardModel に map する（Paging 3 公式 `PagingData.map`）。
            crossFeedRepository.pagingData().map { paging ->
                paging.map { TimelineCardModelMapper.toCardModel(it) }
            }
        }
    }

    private companion object {
        /**
         * mockMode で MockTimelineItem を ArticleCardModel に変換する際の fallback ISO 値。
         *
         * MockTimelineItem.publishedAt は事前整形された相対表現文字列なので、
         * RelativeTimeFormatter（Issue #27）が解釈できる ISO-8601 値を流し込む必要がある。
         * 一定の固定値（リリースの semantic version とは無関係）で十分（開発時起動用途のため）。
         */
        const val MOCK_FALLBACK_PUBLISHED_AT_ISO: String = "2026-06-12T11:30:00Z"
    }
}

// 旧 `TimelineUiState` は Issue #33 で削除した（カード描画は LazyPagingItems を直接
// 購読する Compose 側に集約したため）。
