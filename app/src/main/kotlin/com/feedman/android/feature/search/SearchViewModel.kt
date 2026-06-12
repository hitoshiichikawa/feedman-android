package com.feedman.android.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.feedman.android.core.data.ItemStateFailure
import com.feedman.android.core.data.ItemStateOverlay
import com.feedman.android.core.data.ItemStateStore
import com.feedman.android.core.data.SearchRepository
import com.feedman.android.core.model.ItemSearchHit
import com.feedman.android.core.ui.ArticleCardModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 横断検索画面の ViewModel（Issue #47 / Issue #48 / Req 1 / 2 / 3 / 5 / 6）。
 *
 * 状態の正本は 2 つの `StateFlow` に分割する:
 *
 * - [queryInput]: 入力欄の現在値（Req 1.4 / 1.5 / 2.3 / NFR 1.1）。テキスト変更は常に
 *   こちらにのみ反映され、サーバー呼び出しは発生しない（NFR 1.2）
 * - [submittedQuery]: 検索を確定したキーワード（前後空白除去後 / Req 3.1）。`null` は
 *   「未送信 = 空クエリ状態」を示し、Pager Flow は emptyFlow となる（Req 2.1 / 3.2）
 *
 * [resultsPaging] / [cardPagingData] は [submittedQuery] の変化に追随し、null → emptyFlow、
 * 非 null → 新しい Pager を生成する（Req 5.4: キーワード変更で先頭ページから取り直し）。
 *
 * ## Issue #48 — 検索→詳細ブリッジ（Req 1〜3）
 *
 * 既読 / スターの楽観的更新は [ItemStateStore] が単一データ点として保持する（Issue #38 と同様）。
 * 本 VM は:
 *
 * - `cachedIn` 後の `PagingData<ArticleCardModel>` と `store.overlays` を `combine` し、
 *   overlay 値をサーバー由来 [ItemSearchHit] の `isRead` / `isStarred` より優先して
 *   各カードに解決する（Issue #48 Req 3.1〜3.6）
 * - 外部リンク起動成功後の既読化を `store.markRead` 経由で発火（Issue #48 Req 2.3）。
 *   失敗時 snackbar は store の `failures` 経由で発火する（Issue #48 Req 2.4）
 * - スタートグルは [toggleStar] が `store.setStarred` を呼び出し、楽観的反映とサーバー反映
 *   失敗時のロールバックを store に委譲する（タイムライン / スター画面と同じ流儀）
 * - 外部リンク自体の起動失敗（OpenLinkResult.InvalidUrl / NoAppToHandle）は
 *   [SearchExternalLinkEvent.OpenLinkFailed] を流す（Issue #48 Req 2.4）
 *
 * ## 設計上の補足
 *
 * - `flatMapLatest` で submittedQuery の変化に合わせて Flow を切替える。前回の Pager 購読は
 *   破棄され、追従中の append/refresh も中断する（Req 5.4）。
 * - `cachedIn(viewModelScope)` で再コンポジション時のキャッシュを保持する（再回転・他画面
 *   経由復帰）。
 * - サジェストチップは Req 2.2 でプロトタイプ準拠の静的リストとし、ViewModel は
 *   [SUGGESTIONS] 定数を公開する（UI が `stringResource` 経由ではなく直接表示する）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val itemStateStore: ItemStateStore,
) : ViewModel() {

    private val _queryInput = MutableStateFlow("")

    /** 入力欄の現在値（Req 1.4 / 1.5 / 2.3 / NFR 1.1）。 */
    val queryInput: StateFlow<String> = _queryInput.asStateFlow()

    private val _submittedQuery = MutableStateFlow<String?>(null)

    /**
     * 検索を確定したキーワード（前後空白除去後 / Req 3.1）。`null` は未送信＝空クエリ状態
     * （Req 2.1 / 3.2）。
     */
    val submittedQuery: StateFlow<String?> = _submittedQuery.asStateFlow()

    /**
     * 検索ヒット PagingData の Flow（Req 3.3 / 5.4）。
     *
     * [submittedQuery] が `null` のときは [emptyFlow]（Req 2.1 / 3.2: サーバー呼び出しなし）、
     * 非 null のときは [SearchRepository.pagingData] が返す `Flow<PagingData<ItemSearchHit>>`
     * を流す。Pager 出力は `cachedIn(viewModelScope)` で再コンポジション間のキャッシュを保持する。
     *
     * `flatMapLatest` を使うことで、新しい [submittedQuery] が来たタイミングで前回の購読を
     * 破棄し、新しいキーワードで先頭ページから取得を開始する（Req 5.4）。
     *
     * 描画側（[SearchScreen]）は本 Flow をそのまま使わず、[cardPagingData] を購読する
     * （Issue #48 で overlay 合成が必要になったため）。本 Flow は #47 由来のテストで
     * `pagingData(query)` 呼び出しを確認するための API 表面として温存する。
     */
    val resultsPaging: Flow<PagingData<ItemSearchHit>> =
        submittedQuery.flatMapLatest { query ->
            if (query == null) {
                emptyFlow()
            } else {
                searchRepository.pagingData(query)
            }
        }.cachedIn(viewModelScope)

    /**
     * カード描画用 PagingData の Flow（Issue #48 Req 3.1〜3.6）。
     *
     * - [SearchCardModelMapper.toCardModel] で [ItemSearchHit] → [ArticleCardModel] に変換
     *   （published_at が null のときの override は描画側 stringResource で別途解決する。
     *   本 ViewModel は Android リソースに依存しないため、UI 側 SearchScreen が flow を再生成
     *   する [cardPagingFlow] を引き続き利用する）。本 [cardPagingData] は overlay 合成の
     *   骨格をテストで検証するための内部表面として用い、UI は本 Flow を直接購読しない構造を
     *   維持する。
     * - `cachedIn(viewModelScope)` で再コンポジション間のキャッシュを保持する
     * - [ItemStateStore.overlays] と `combine` し、`isRead` / `isStarred` を overlay 優先で
     *   上書きする（Issue #48 Req 3.1〜3.4）。overlay が無い ID はサーバー由来値を維持する
     *   （Issue #48 Req 3.5）。
     *
     * 文字列リソース（`search_published_at_unknown`）の解決は UI 層の責務として残しているため、
     * 本 Flow は `relativeTimeOverride = null` を持つカードを流す（Composable 側が必要に応じて
     * 上書きする想定）。本 Flow を UI が購読する場合、`published_at == null` のメタ行は
     * `RelativeTimeFormatter` のフォールバックが効く（[SearchCardModelMapper.UNKNOWN_PUBLISHED_AT]
     * 経由）。
     */
    val cardPagingData: Flow<PagingData<ArticleCardModel>> =
        submittedQuery.flatMapLatest { query ->
            if (query == null) {
                emptyFlow()
            } else {
                searchRepository.pagingData(query).map { paging ->
                    paging.map { hit ->
                        SearchCardModelMapper.toCardModel(
                            hit = hit,
                            unknownLabel = SearchCardModelMapper.UNKNOWN_PUBLISHED_AT,
                        )
                    }
                }
            }
        }
            .cachedIn(viewModelScope)
            .combineWithOverlays(itemStateStore.overlays)

    /**
     * テスト用に公開する変換ロジック本体（Issue #48 / NFR 2.3）。
     *
     * 本番 [cardPagingData] は `submittedQuery`（StateFlow）と `itemStateStore.overlays`
     * （StateFlow）双方が完了しない StateFlow を上流に持つため、`asSnapshot()` が
     * テスト終了時に `UncompletedCoroutinesError` を起こす。本テスト用 API は
     * `submittedQuery.value` を **検索確定後に呼ぶ前提**で、対象クエリを 1 回だけ
     * [SearchRepository.pagingData] に渡し、`overlays` も `take(1)` で 1 スナップショット
     * だけ取って合成・完了させる（TimelineViewModel / StarredViewModel / FeedScreenViewModel と同じ流儀）。
     *
     * テストは `viewModel.submit()` を呼んでから本 API を呼ぶこと（呼ぶ前に `submittedQuery`
     * が null なら空 Flow を返す）。
     */
    internal fun cardPagingDataForTest(): Flow<PagingData<ArticleCardModel>> {
        val query = submittedQuery.value ?: return emptyFlow()
        return searchRepository.pagingData(query)
            .map { paging ->
                paging.map { hit ->
                    SearchCardModelMapper.toCardModel(
                        hit = hit,
                        unknownLabel = SearchCardModelMapper.UNKNOWN_PUBLISHED_AT,
                    )
                }
            }
            .combineWithOverlays(itemStateStore.overlays.take(1))
    }

    // ── Issue #48: 外部リンク起動失敗の one-shot 通知 ──────────────────

    private val _externalLinkEvents = MutableSharedFlow<SearchExternalLinkEvent>(
        replay = 0,
        extraBufferCapacity = 4,
    )

    /**
     * 外部リンク起動自体の失敗（InvalidUrl / NoAppToHandle）の one-shot 通知（Issue #48 Req 2.4）。
     *
     * 既読化 / スター更新の失敗は [itemStateFailures] に統一されているため、UI 側は
     * 双方を購読する。
     */
    val externalLinkEvents: SharedFlow<SearchExternalLinkEvent> = _externalLinkEvents.asSharedFlow()

    /**
     * Issue #38 / Issue #48 Req 2.4: ItemStateStore の楽観的更新失敗を画面へ再公開する。
     * UI 側は本 VM のみを観測すれば良い構造を維持する。
     */
    val itemStateFailures: SharedFlow<ItemStateFailure> = itemStateStore.failures

    /**
     * 外部リンク起動成功時の既読化（Issue #48 Req 2.3）。
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
     * カードのスタートグル委譲（Issue #38 Req 1.1 / 4.4 / Issue #48 Req 3.4）。
     *
     * 検索結果カード自体には専用スタートグル UI を持たないが（requirements.md Out of Scope）、
     * 詳細シート由来のスタートグルが本 store 経由で `overlays` に反映され、検索結果カードの
     * 表示が即時同期される（Issue #48 Req 3.4）。本メソッドは将来 UI 側でスタートグルを
     * 露出する場合の API 表面を兼ね、テスト上も同じ流儀で検証できるよう公開する。
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
     * 外部リンク起動失敗時の通知（Issue #48 Req 2.4）。
     *
     * URL 不正（InvalidUrl）や対応アプリ不在（NoAppToHandle）を UI へ流すための API。
     * 既読化は走らない（呼び出し側で markReadOnExternalOpen を呼ばないことで保証）。
     */
    fun notifyExternalLinkFailed() {
        viewModelScope.launch {
            _externalLinkEvents.emit(SearchExternalLinkEvent.OpenLinkFailed)
        }
    }

    /**
     * 入力欄の値を変更する（Req 1.4 / 1.5 / 2.3 / NFR 1.1）。
     *
     * サーバー呼び出しは発生しない。クリア操作（Req 1.5）から呼ばれる場合も同じ経路で、
     * 直前に確定済みの [submittedQuery] は **意図的に維持しない**: クリアは「空クエリ状態に
     * 戻す」挙動なので [submittedQuery] も null に戻すために、UI 側がクリア操作で
     * [clear] を呼ぶ。
     *
     * @param value 入力欄の新しい値（未トリム）
     */
    fun onQueryChanged(value: String) {
        _queryInput.value = value
    }

    /**
     * 入力欄を空に戻し、空クエリ表示（Req 1.5 / 2.1）に切り替える。
     *
     * UI 側のクリアボタン（Req 1.4 / 1.5）から呼ぶ。`submittedQuery` を null に戻すことで
     * [cardPagingData] が `emptyFlow` に切り替わり、結果リストが消える。
     */
    fun clear() {
        _queryInput.value = ""
        _submittedQuery.value = null
    }

    /**
     * 入力欄の現在値を送信して検索を確定する（Req 3.1 / 3.2 / 3.3 / 5.4）。
     *
     * - 前後の空白を除去（Req 3.1）
     * - 除去後に空文字なら何もしない（Req 3.2: サーバー呼び出し発生せず、空クエリ表示維持）
     * - 非空のときは [submittedQuery] を更新 → [cardPagingData] が新 Pager に切り替わる
     *   （Req 3.3 / 5.4）
     */
    fun submit() {
        val trimmed = _queryInput.value.trim()
        if (trimmed.isEmpty()) return
        _submittedQuery.value = trimmed
    }

    /**
     * サジェストチップ選択（Req 2.3 / 2.4）。
     *
     * - 当該チップの文字列を入力欄に投入する（Req 2.3）
     * - そのまま [submit] と同じフローで検索を開始する（Req 2.4）
     */
    fun selectSuggestion(suggestion: String) {
        _queryInput.value = suggestion
        submit()
    }

    companion object {
        /**
         * Req 2.2: プロトタイプ `FMSearchScreen` の suggestions と同じ静的候補。
         *
         * 利用者ごとに動的生成する仕組みは v1 の Out of Scope（requirements.md Out of Scope）。
         */
        val SUGGESTIONS: List<String> = listOf("Go", "Kubernetes", "OpenAI", "TypeScript", "Rust")
    }
}

/**
 * `Flow<PagingData<ArticleCardModel>>` を [ItemStateStore.overlays] と `combine` し、
 * 各カードの isRead / isStarred を overlay 優先で上書きする（Issue #48 Req 3.1〜3.5）。
 *
 * `cachedIn` の **後** に呼ばれることを前提とする（本番経路）。テスト経路では
 * `take(1)` で overlay スナップショットを 1 回だけ取って合成し、終了させる
 * （TimelineViewModel / StarredViewModel と同じ流儀）。
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
