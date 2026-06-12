package com.feedman.android.core.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import com.feedman.android.core.model.ItemSummary
import com.feedman.android.core.network.FeedmanApi
import com.feedman.android.core.network.paging.CursorPage
import com.feedman.android.core.network.paging.CursorPagingSource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [FeedItemsRepository] の本実装（Issue #40）。
 *
 * [FeedmanApi.getFeedItems] を [CursorPagingSource] 経由で呼び出し、フィード ID とフィルタの
 * 組み合わせごとに独立した [Pager] を生成する。横断新着の [CrossFeedRepositoryImpl] と異なり
 * セッション固有のクエリ（`since_time` 等）は持たず、本リポジトリでは feedId / filter / cursor
 * の 3 値のみをサーバーに送る。
 *
 * ## フィルタ変更時の挙動（Req 2）
 *
 * UI 側が filter を変えたタイミングで [pagingData] が呼び直されるたびに、新しい [Pager]
 * （= 新しい [PagingSource]）が返る。新 Pager の初回ロードは `LoadParams.Refresh(key = null)`
 * で開始されるため、自動的に先頭ページ（カーソル未指定）から取得が開始され、直前フィルタの
 * ページ蓄積は新 Pager の状態には引き継がれない（Req 2.1 / 2.2）。
 *
 * ## エラー伝播（Req 3.4 / 3.5 / 3.6）
 *
 * 非 2xx / I/O 失敗は [com.feedman.android.core.network.FeedmanException] が
 * [CursorPagingSource] 経由で `LoadResult.Error` として上位レイヤーに露出する。Paging 3 の
 * `retry()` は失敗時の `LoadParams.key`（= 失敗時のカーソル）と同一値で `load()` を再呼び出し
 * するため、同一 feedId / 同一 filter / 同一カーソルでの再試行が成立する（Req 3.6）。
 *
 * ## DI
 *
 * `RepositoryModule` で [FeedItemsRepository] にバインドされる（Hilt @Binds）。
 *
 * @property api Retrofit ベースの Feedman API クライアント
 */
@Singleton
class FeedItemsRepositoryImpl @Inject constructor(
    private val api: FeedmanApi,
) : FeedItemsRepository {

    /** ページサイズ（SPEC §4.2: 50 件/回・上限 200）。Issue #32 の [CrossFeedRepositoryImpl] と統一。 */
    private val pageSize: Int = PAGE_SIZE

    override fun pagingData(feedId: String, filter: FeedItemFilter): Flow<PagingData<ItemSummary>> {
        val pager = Pager(
            config = PagingConfig(
                pageSize = pageSize,
                enablePlaceholders = false,
                initialLoadSize = pageSize,
            ),
            pagingSourceFactory = { newPagingSource(feedId = feedId, filter = filter) },
        )
        return pager.flow
    }

    /**
     * 指定 feedId / filter 向けの [PagingSource] を新規に生成する。
     *
     * テストから直接呼べるように `internal` 可視性で公開している（[CrossFeedRepositoryImpl] と同流儀）。
     */
    internal fun newPagingSource(feedId: String, filter: FeedItemFilter): PagingSource<String, ItemSummary> {
        return CursorPagingSource { cursor -> loadPage(feedId = feedId, filter = filter, cursor = cursor) }
    }

    /**
     * [CursorPagingSource] の loader 本体。
     *
     * - cursor が `null` のとき = 初回ロード（先頭ページ）。Req 1.1 / 2.1 / 4.1 で要求される
     *   「カーソル未指定で先頭から取得」のための分岐。
     * - cursor が非 null のとき = 後続ロード。Req 2.3: 前ページの `next_cursor` をそのまま搬送する。
     * - filter は呼び出し側で固定された [FeedItemFilter] の [FeedItemFilter.queryValue] を送信する
     *   （Req 1.1 / 1.2 / 1.3）。Retrofit は `null` Query を URL に乗せないが、本リポジトリでは
     *   常に enum 値を解決して送るため、`?filter=` キーは常に付与される。
     * - feedId は呼び出し元から受け取った値をそのまま `@Path` に渡す（Req 1.4）。
     *
     * 終端判定（Req 3.1 / 3.2 / 3.3）とエラー透過（Req 3.4 / 3.5）は [CursorPagingSource] に委譲する。
     */
    private suspend fun loadPage(
        feedId: String,
        filter: FeedItemFilter,
        cursor: String?,
    ): CursorPage<ItemSummary> {
        val response = api.getFeedItems(
            feedId = feedId,
            filter = filter.queryValue,
            cursor = cursor,
            limit = pageSize,
        )
        return CursorPage(
            items = response.items,
            nextCursor = response.nextCursor,
            hasMore = response.hasMore,
        )
    }

    companion object {
        /** SPEC §4.2: 50 件/回。 */
        const val PAGE_SIZE: Int = 50
    }
}
