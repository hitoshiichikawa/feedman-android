package com.feedman.android.feature.starred

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.feedman.android.core.data.ItemStateFailure
import com.feedman.android.core.data.ItemStateOverlay
import com.feedman.android.core.data.ItemStateStore
import com.feedman.android.core.data.StarredItemsRepository
import com.feedman.android.core.ui.ArticleCardModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import javax.inject.Inject

/**
 * スター一覧画面の ViewModel（Issue #46）。
 *
 * `StarredItemsRepository` から流れる `Flow<PagingData<StarredItemSummary>>` を
 * `Flow<PagingData<ArticleCardModel>>` に変換し、`ItemStateStore.overlays` と合成して
 * 楽観的更新を即時反映する（Req 5.1 / 5.2）。スター解除時の残置表示（Req 5.3）は
 * **意図的にリストから除去しない**: overlay の `isStarred=false` を尊重して
 * [ArticleCardModel.isStarred] を `false` に切り替えるだけ（カード本体は描画され続け、
 * `StarToggle` は outline 表示になる / Req 5.3）。
 *
 * リフレッシュまたは再入場で除去（Req 5.4）は **サーバー側の filter で自動的に成立** する:
 * サーバーは `is_starred=true` の記事のみを返すため、解除確定済みの記事は次回の
 * 先頭ページ取得時点でレスポンスに含まれない。本 VM 側で独自の除去ロジックは持たない。
 *
 * ## 公開 API
 *
 * - [cardPagingData]: 画面が `collectAsLazyPagingItems()` で購読する Flow（overlay 合成済み）
 * - [itemStateFailures]: `ItemStateStore.failures` の再公開（Req 5.5 のロールバック通知）
 * - [toggleStar]: スタートグル（[ItemStateStore.setStarred] 委譲）
 */
@HiltViewModel
class StarredViewModel @Inject constructor(
    private val starredItemsRepository: StarredItemsRepository,
    private val itemStateStore: ItemStateStore,
) : ViewModel() {

    /**
     * Issue #38 / Req 5.5: `ItemStateStore.failures` を画面へ再公開する。
     * UI 側は本 VM のみを観測すれば良い構造を維持する。
     */
    val itemStateFailures: SharedFlow<ItemStateFailure> = itemStateStore.failures

    /**
     * カード描画用 PagingData の Flow（overlay 合成済み）。
     *
     * `cachedIn(viewModelScope)` で再コンポジション時のキャッシュを保持し、`overlays` と
     * `combine` して各カードの isRead / isStarred を overlay 値で上書きする（Req 5.1 / 5.2 / 5.3）。
     */
    val cardPagingData: Flow<PagingData<ArticleCardModel>> =
        starredItemsRepository.pagingData()
            .map { paging -> paging.map(StarredCardModelMapper::toCardModel) }
            .cachedIn(viewModelScope)
            .combineWithOverlays(itemStateStore.overlays)

    /**
     * テスト用に公開する変換ロジック本体。`cachedIn(viewModelScope)` を経由せず、
     * `take(1)` で overlay スナップショットを 1 回取って合成する（TimelineViewModel と同じ流儀）。
     */
    internal fun cardPagingDataForTest(): Flow<PagingData<ArticleCardModel>> {
        return starredItemsRepository.pagingData()
            .map { paging -> paging.map(StarredCardModelMapper::toCardModel) }
            .combineWithOverlays(itemStateStore.overlays.take(1))
    }

    /**
     * Issue #38 Req 1.1 / 4.4 / Issue #46 Req 5.1: スタートグル。
     *
     * [ItemStateStore.setStarred] が楽観的反映・サーバー反映・失敗時ロールバック・通知までを担う。
     * スター解除時（`newState=false`）でもリポジトリ側のページングデータは破棄せず、overlay
     * 反映のみが行われるため、カードはリスト上に残置されたまま `StarToggle` が outline に切り替わる
     * （Req 5.3）。
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

    // Req 3.4: リフレッシュ失敗の通知は UI 側で `LazyPagingItems.loadState.refresh` を直接
    // 観測する設計（TimelineScreen と同等）のため、本 VM は専用の SharedFlow を持たない。
}

/**
 * `Flow<PagingData<ArticleCardModel>>` を [ItemStateStore.overlays] と `combine` し、
 * 各カードの isRead / isStarred を overlay 優先で上書きする（Issue #46 Req 5.1〜5.3）。
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
