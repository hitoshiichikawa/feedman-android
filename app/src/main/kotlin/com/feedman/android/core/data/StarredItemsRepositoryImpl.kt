package com.feedman.android.core.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import com.feedman.android.core.model.StarredItemSummary
import com.feedman.android.core.network.FeedmanApi
import com.feedman.android.core.network.paging.CursorPage
import com.feedman.android.core.network.paging.CursorPagingSource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [StarredItemsRepository] の本実装（Issue #46）。
 *
 * [FeedmanApi.getStarredItems] を [CursorPagingSource] 経由で呼び出す。スター一覧は
 * セッション保持クエリ（横断新着の `since_time` のような連続性確保用パラメータ）を
 * 持たないため、リポジトリ内部に保持する状態は無く、Pager の生成と loader の橋渡しのみ
 * を担う（[FeedItemsRepositoryImpl] と同じ流儀）。
 *
 * ## エラー伝播（Req 2.5 / 2.6）
 *
 * 非 2xx / I/O 失敗は [com.feedman.android.core.network.FeedmanException] が
 * [CursorPagingSource] 経由で `LoadResult.Error` として上位レイヤーに露出する。Paging 3 の
 * `retry()` は失敗時の `LoadParams.key`（= 失敗時のカーソル）と同一値で `load()` を再呼び出し
 * するため、同一カーソルでの再試行が成立する。
 *
 * ## DI
 *
 * `RepositoryModule` で [StarredItemsRepository] にバインドされる（Hilt @Binds）。
 *
 * @property api Retrofit ベースの Feedman API クライアント
 */
@Singleton
class StarredItemsRepositoryImpl @Inject constructor(
    private val api: FeedmanApi,
) : StarredItemsRepository {

    /** ページサイズ（SPEC §4.2: 50 件/回）。他リポジトリと統一。 */
    private val pageSize: Int = PAGE_SIZE

    override fun pagingData(): Flow<PagingData<StarredItemSummary>> {
        val pager = Pager(
            config = PagingConfig(
                pageSize = pageSize,
                enablePlaceholders = false,
                initialLoadSize = pageSize,
            ),
            pagingSourceFactory = { newPagingSource() },
        )
        return pager.flow
    }

    /**
     * 新しい [PagingSource] を生成する（テストから直接呼べるよう `internal` 公開）。
     *
     * Pager のリフレッシュ時にも本メソッドが呼ばれ、新しい PagingSource が先頭ページから
     * 取得し直す（Req 3.2 / 3.3）。
     */
    internal fun newPagingSource(): PagingSource<String, StarredItemSummary> {
        return CursorPagingSource(loader = ::loadPage)
    }

    /**
     * [CursorPagingSource] の loader 本体。
     *
     * - cursor が `null` のとき = 初回ロード。Req 2.1: cursor 未指定で取得する。
     * - cursor が非 null のとき = 後続ロード。Req 2.2: 前ページの `next_cursor` をそのまま搬送する。
     * - 終端判定（Req 2.3 / 2.4）とエラー透過（Req 2.5 / 2.6）は [CursorPagingSource] に委譲。
     */
    private suspend fun loadPage(cursor: String?): CursorPage<StarredItemSummary> {
        val response = api.getStarredItems(cursor = cursor, limit = pageSize)
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
