package com.feedman.android.feature.feed

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.feedman.android.core.data.FeedItemsRepository
import com.feedman.android.core.data.ItemStateOverlay
import com.feedman.android.core.data.ItemStateStore
import com.feedman.android.core.data.SubscriptionRepository
import com.feedman.android.core.model.ItemSummary
import com.feedman.android.core.model.Subscription
import com.feedman.android.core.network.FeedmanException
import com.feedman.android.core.ui.ArticleCardModel
import com.feedman.android.shell.AppRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * フィード別画面（Issue #41 / SPEC §5.2）の ViewModel。
 *
 * NavHost の `feed/{feedId}` ルートから渡される `feedId` を [SavedStateHandle] 経由で
 * 受け取り、以下を統括する:
 *
 * - 上部フィルタタブの選択状態 [currentFilter]（Req 2.1 / 2.2 / 2.3 / 2.7）
 * - [FeedItemsRepository] と [ItemStateStore] を combine した [cardPagingData]（Req 1.1 /
 *   1.2 / 2.4）。フィルタ変更時は `flatMapLatest` で新しい Pager に切替わり、自動的に
 *   先頭ページから取得開始（Req 2.4 のスクロール先頭リセットは Paging 3 の Pager 切替で担保）
 * - 購読情報の観測（[banner] / [subscription]）（Req 3.1〜3.4 / 4.1 / 4.2 / 4.3）
 * - 再開アクション [onResumeBannerTap]（Req 3.5 / 3.6 / 3.7 / 3.8）
 * - 外部リンク失敗通知 [notifyExternalLinkFailed]（Issue #37 流用）
 * - 既読化 [markReadOnExternalOpen]・スタートグル [toggleStar]（[ItemStateStore] 委譲）
 *
 * 詳細シート起動は AppShell 直下の `ArticleDetailViewModel` を用いるため、本 VM は
 * 詳細シート関連を一切持たない（TimelineScreen と同じ流儀）。
 *
 * @property feedId NavHost から受け取る対象フィード ID（空文字列の場合は呼び出し側のバグ）
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FeedScreenViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val feedItemsRepository: FeedItemsRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val itemStateStore: ItemStateStore,
) : ViewModel() {

    /** Req 1.1: NavHost から渡される対象フィードの ID。空のとき例外で早期検出する。 */
    val feedId: String = requireNotNull(
        savedStateHandle.get<String>(AppRoute.Feed.ARG_FEED_ID),
    ) { "feedId is required by FeedScreenViewModel" }

    init {
        require(feedId.isNotBlank()) { "feedId must not be blank" }
        // Req 4.1 / 4.2: 画面起動時に購読情報を最新化する（既にロード済みなら no-op）。
        viewModelScope.launch { subscriptionRepository.refresh() }
    }

    // ── フィルタタブ ─────────────────────────────────────────────────

    private val _currentFilter = MutableStateFlow(FeedFilter.DEFAULT)

    /** Req 2.2 / 2.3: 現在選択中のフィルタタブ。 */
    val currentFilter: StateFlow<FeedFilter> = _currentFilter.asStateFlow()

    /**
     * Req 2.3 / 2.7: フィルタタブをタップしたときの起点。同じタブを再選択しても
     * `MutableStateFlow` は等値で no-op なので冪等。
     */
    fun selectFilter(filter: FeedFilter) {
        _currentFilter.value = filter
    }

    // ── 購読情報 ────────────────────────────────────────────────────

    /**
     * Req 4.1 / 4.2 / 4.3: 対象フィードの購読情報。`null` のときフィードが未存在 or
     * 取得未完了。UI 側で feedNotFound 判定（Req 4.3）にも使う。
     */
    val subscription: StateFlow<Subscription?> = subscriptionRepository
        .observeFeed(feedId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = null,
        )

    private val _resumeInProgress = MutableStateFlow(false)

    private val _fetchInProgress = MutableStateFlow(false)

    /**
     * Issue #42 Req 1.2 / 1.4: 手動フェッチ進行中フラグ。UI 側で PullToRefreshBox の
     * isRefreshing 表示と重複起動抑止に使う。
     */
    val fetchInProgress: StateFlow<Boolean> = _fetchInProgress.asStateFlow()

    /**
     * Req 3.1〜3.4 / 3.6: 警告バナーの表示状態。[subscription] と [resumeInProgress] から
     * [resolveBanner] で純粋関数的に決定する。
     */
    val banner: StateFlow<FeedStatusBanner> = combine(
        subscription,
        _resumeInProgress,
    ) { sub, inProgress -> resolveBanner(sub, inProgress) }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = FeedStatusBanner.Hidden,
    )

    // ── 一覧ページング ────────────────────────────────────────────

    /**
     * Req 1.1 / 1.2 / 2.3 / 2.4: フィルタ変更時に新しい Pager に切替えるカード PagingData。
     *
     * `flatMapLatest` でフィルタ切替時に直前の Pager の購読を解除しつつ、新しい
     * [FeedItemsRepository.pagingData] を購読する。`cachedIn(viewModelScope)` 後に
     * [ItemStateStore.overlays] と `combine` し、isRead / isStarred を overlay 優先で上書き
     * する（Issue #38 と同じ規約）。
     */
    val cardPagingData: Flow<PagingData<ArticleCardModel>> =
        _currentFilter
            .flatMapLatest { filter ->
                feedItemsRepository
                    .pagingData(feedId = feedId, filter = filter.toFeedItemFilter())
                    .map { paging -> paging.map(::toCardModel) }
            }
            .cachedIn(viewModelScope)
            .combineWithOverlays(itemStateStore.overlays)

    /**
     * テスト用: `cachedIn` を経由しない短命 Flow。`take(1)` で overlays スナップショットを
     * 1 回だけ取り出し、`asSnapshot()` が完了できるようにする（TimelineViewModelTest と
     * 同じ理由）。
     */
    internal fun cardPagingDataForTest(filter: FeedFilter): Flow<PagingData<ArticleCardModel>> {
        return feedItemsRepository
            .pagingData(feedId = feedId, filter = filter.toFeedItemFilter())
            .map { paging -> paging.map(::toCardModel) }
            .combineWithOverlays(itemStateStore.overlays.take(1))
    }

    // ── 既読化 / スタートグル ────────────────────────────────────────

    /**
     * Issue #37 / #38: 外部リンク起動成功時の既読化（[ItemStateStore.markRead] 委譲）。
     */
    fun markReadOnExternalOpen(itemId: String, currentIsRead: Boolean) {
        itemStateStore.markRead(itemId = itemId, currentIsRead = currentIsRead)
    }

    /**
     * Issue #38: スタートグル（[ItemStateStore.setStarred] 委譲）。
     */
    fun toggleStar(itemId: String, newState: Boolean, baselineStarred: Boolean) {
        itemStateStore.setStarred(
            itemId = itemId,
            isStarred = newState,
            baselineStarred = baselineStarred,
        )
    }

    // ── 再開アクション ────────────────────────────────────────────

    private val _events = MutableSharedFlow<FeedScreenEvent>(
        replay = 0,
        extraBufferCapacity = 4,
    )

    /** Req 3.7 / 3.8 / Issue #37 流用: one-shot 通知ストリーム。 */
    val events: SharedFlow<FeedScreenEvent> = _events.asSharedFlow()

    /** Issue #38 Req 2.3 経由: ItemStateStore の失敗通知を UI 側へ再公開する。 */
    val itemStateFailures: SharedFlow<com.feedman.android.core.data.ItemStateFailure> =
        itemStateStore.failures

    /**
     * Req 3.5 / 3.6 / 3.7 / 3.8: 警告バナーの「再開」ボタンタップハンドラ。
     *
     * - 進行中フラグを立てる（Req 3.6）
     * - [SubscriptionRepository.resume] を呼ぶ
     * - 成功時: [FeedScreenEvent.ResumeSucceeded] を流す（Req 3.7）。`subscription` Flow が
     *   active に切り替わるため `banner` も自動的に [FeedStatusBanner.Hidden] になる
     * - 失敗時: [FeedScreenEvent.ResumeFailed] を流す（Req 3.8）。`banner` は表示のまま
     * - finally で進行中フラグを下ろす
     *
     * 連打防止は UI 側の `enabled = !resumeInProgress` で行う前提だが、ViewModel 側でも
     * inflight 中の追加呼び出しを早期 return することで二重起動を抑止する。
     */
    fun onResumeBannerTap() {
        if (_resumeInProgress.value) return
        val currentSub = subscription.value ?: return // Req 4.3 側で feedNotFound 表示
        _resumeInProgress.value = true
        viewModelScope.launch {
            try {
                subscriptionRepository.resume(currentSub.id)
                _events.emit(FeedScreenEvent.ResumeSucceeded)
            } catch (e: FeedmanException) {
                _events.emit(
                    FeedScreenEvent.ResumeFailed(
                        message = e.errorMessage.ifBlank {
                            FeedmanException.FALLBACK_UNKNOWN_MESSAGE
                        },
                    ),
                )
            } catch (e: Exception) {
                // 想定外の例外（バグ起因 / UnsupportedOperationException 等）も silent fail させない。
                _events.emit(
                    FeedScreenEvent.ResumeFailed(
                        message = e.message?.takeIf { it.isNotBlank() }
                            ?: FeedmanException.FALLBACK_UNKNOWN_MESSAGE,
                    ),
                )
            } finally {
                _resumeInProgress.value = false
            }
        }
    }

    /**
     * Issue #42 Req 1.1 / 1.2 / 1.4 / 2.1 / 3.x / 4.x: Pull-to-refresh ジェスチャ完了時の
     * ハンドラ。
     *
     * - 進行中フラグが true なら **早期 return** で重複起動を抑止（Req 1.4）
     * - `subscription.value == null` のとき（フィード未存在）はフェッチ要求自体を発行しない
     * - 進行中フラグを true に立てて [SubscriptionRepository.fetch] を呼ぶ
     * - 成功時: [FeedScreenEvent.FetchSucceeded] を流す（UI 側は Paging refresh を起動 /
     *   Req 2.1）。Subscription の最新化は Repository 内部で `_subscriptions` 更新済み
     *   なのでドロワー未読バッジは自動反映される（Req 2.3）。
     * - クールダウン（code = `FEED_COOLDOWN`）: [FeedScreenEvent.FetchCooldown] を
     *   `retryAfterSeconds` 付きで流す。UI 側で残り秒数の有無による文言切替を行う
     *   （Req 3.1 / 3.2 / 3.3）。
     * - その他失敗: [FeedScreenEvent.FetchFailed] を流す（Req 4.1〜4.3）。`code` が
     *   `NETWORK_ERROR` のときは [FeedmanException.FALLBACK_NETWORK_MESSAGE] を採用する
     *   （Req 4.3）。
     * - finally で進行中フラグを下ろす（NFR 1.2: 応答受領後 500ms 以内に終了状態へ）
     */
    fun onPullToRefresh() {
        if (_fetchInProgress.value) return
        val currentSub = subscription.value ?: return
        _fetchInProgress.value = true
        viewModelScope.launch {
            try {
                subscriptionRepository.fetch(currentSub.id)
                _events.emit(FeedScreenEvent.FetchSucceeded)
            } catch (e: FeedmanException) {
                val event = when (e.code) {
                    FeedmanException.CODE_FEED_COOLDOWN -> FeedScreenEvent.FetchCooldown(
                        retryAfterSeconds = e.retryAfterSeconds,
                    )
                    FeedmanException.CODE_NETWORK_ERROR -> FeedScreenEvent.FetchFailed(
                        message = e.errorMessage.ifBlank {
                            FeedmanException.FALLBACK_NETWORK_MESSAGE
                        },
                    )
                    else -> FeedScreenEvent.FetchFailed(
                        message = e.errorMessage.ifBlank {
                            FeedmanException.FALLBACK_UNKNOWN_MESSAGE
                        },
                    )
                }
                _events.emit(event)
            } catch (e: Exception) {
                // 想定外の例外も silent fail させない。
                _events.emit(
                    FeedScreenEvent.FetchFailed(
                        message = e.message?.takeIf { it.isNotBlank() }
                            ?: FeedmanException.FALLBACK_UNKNOWN_MESSAGE,
                    ),
                )
            } finally {
                _fetchInProgress.value = false
            }
        }
    }

    /** Issue #37 流用: 外部リンク起動失敗通知。 */
    fun notifyExternalLinkFailed() {
        viewModelScope.launch {
            _events.emit(FeedScreenEvent.OpenLinkFailed)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/**
 * [ItemSummary]（フィード別記事一覧の API レスポンス 1 件）を共有カード [ArticleCardModel] に
 * 変換する純粋関数（Issue #41 Req 1.2）。
 *
 * 横断タイムラインの `TimelineCardModelMapper` と同じく、`hatebu_fetched_at` が null のとき
 * は HatebuBadge が「−」表示にフォールバックする（Issue #27 Req 2.2）ため、ItemSummary の
 * 当該フィールドをそのまま渡す。横断タイムラインと違い ItemSummary は本来 `hatebuFetchedAt`
 * を持つため、値が来ていればそのまま使う。
 *
 * フィード別画面の Subscription 側で favicon / feedTitle を持つため、カード上の faviconValue は
 * null のままにし、UI 側で（必要なら）画面共通の favicon を別経路で渡す方針を維持する
 * （プロトタイプ FMFeedScreen 側でもフィード別カードはフィード名を冗長表示しない）。
 */
internal fun toCardModel(item: ItemSummary): ArticleCardModel = ArticleCardModel(
    id = item.id,
    title = item.title,
    feedTitle = "", // フィード別画面ではソース名を冗長表示しない（fm-screens.jsx 準拠）
    faviconValue = null,
    publishedAtIso = item.publishedAt,
    isDateEstimated = item.isDateEstimated,
    isRead = item.isRead,
    isStarred = item.isStarred,
    hatebuCount = item.hatebuCount,
    hatebuFetchedAt = item.hatebuFetchedAt,
    summary = item.summary,
    link = item.link,
)

/**
 * `Flow<PagingData<ArticleCardModel>>` を [ItemStateStore.overlays] と `combine` し、
 * 各カードの isRead / isStarred を overlay 優先で上書きする（TimelineViewModel と同じ規約）。
 *
 * `cachedIn` の **後** に呼ばれることを前提とする（本番経路）。テスト経路では
 * `take(1)` で overlay スナップショットを 1 回だけ取って合成し、終了させる。
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
