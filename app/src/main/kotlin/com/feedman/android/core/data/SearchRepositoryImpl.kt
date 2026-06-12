package com.feedman.android.core.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import com.feedman.android.core.model.ItemSearchHit
import com.feedman.android.core.network.FeedmanApi
import com.feedman.android.core.network.paging.CursorPage
import com.feedman.android.core.network.paging.CursorPagingSource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SearchRepository] の本実装（Issue #47）。
 *
 * [FeedmanApi.searchItems] を [CursorPagingSource] 経由で呼び出し、キーワードと
 * `scope=global` 固定でページングする。呼び出し元から受け取ったキーワードは
 * Pager のクロージャに **そのまま保持** され、後続ページ要求でも同一値が送信される
 * （Req 5.5）。
 *
 * キーワード変更時は呼び出し側が [pagingData] を新しいキーワードで呼び直す前提で、
 * その結果として **新しい [Pager] が生成**される。新 Pager の初回ロードは
 * `LoadParams.Refresh(key = null)` で開始されるため、自動的に先頭ページから取得が
 * 始まり、直前キーワードのページ蓄積は引き継がれない（Req 5.4）。
 *
 * ## エラー伝播（Req 6.2 / 6.4）
 *
 * 非 2xx / I/O 失敗は [com.feedman.android.core.network.FeedmanException] が
 * [CursorPagingSource] 経由で `LoadResult.Error` として上位レイヤーに露出する。Paging 3 の
 * `retry()` は失敗時の `LoadParams.key`（= 失敗時のカーソル）と同一値で `load()` を再呼び出し
 * するため、同一キーワード / 同一カーソルでの再試行が成立する。
 *
 * ## DI
 *
 * `RepositoryModule` で [SearchRepository] にバインドされる（Hilt @Binds）。
 *
 * @property api Retrofit ベースの Feedman API クライアント
 */
@Singleton
class SearchRepositoryImpl @Inject constructor(
    private val api: FeedmanApi,
) : SearchRepository {

    /** ページサイズ（SPEC §4.2: 50 件/回）。他リポジトリと統一。 */
    private val pageSize: Int = PAGE_SIZE

    override fun pagingData(query: String): Flow<PagingData<ItemSearchHit>> {
        // Req 3.2 / 5.4 の呼び出し前提: 空クエリは呼び出し元（ViewModel / 画面）で
        // 弾いてから本メソッドが呼ばれる。仕様逸脱を早期に検出するため require で守る。
        require(query.isNotEmpty()) { "query must not be empty" }
        val pager = Pager(
            config = PagingConfig(
                pageSize = pageSize,
                enablePlaceholders = false,
                initialLoadSize = pageSize,
            ),
            pagingSourceFactory = { newPagingSource(query = query) },
        )
        return pager.flow
    }

    /**
     * 指定キーワード向けの [PagingSource] を新規に生成する。
     *
     * テストから直接呼べるよう `internal` 可視性で公開する（[StarredItemsRepositoryImpl]
     * 等と同流儀）。
     */
    internal fun newPagingSource(query: String): PagingSource<String, ItemSearchHit> {
        return CursorPagingSource { cursor -> loadPage(query = query, cursor = cursor) }
    }

    /**
     * [CursorPagingSource] の loader 本体。
     *
     * - cursor が `null` のとき = 初回ロード（Req 3.3: カーソル未指定で先頭ページ）
     * - cursor が非 null のとき = 後続ロード（Req 5.1: 前ページの `next_cursor` を搬送）
     * - `scope` は常に `"global"` を送信する（Req 3.3 / 7.2）
     * - `q` はセッション開始時に確定したキーワードを後続ページ要求にも保持する（Req 5.5）
     *
     * 終端判定（Req 5.2 / 5.3）とエラー透過（Req 6.2 / 6.4）は
     * [CursorPagingSource] に委譲する。
     */
    private suspend fun loadPage(
        query: String,
        cursor: String?,
    ): CursorPage<ItemSearchHit> {
        val response = api.searchItems(
            query = query,
            scope = SCOPE_GLOBAL,
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

        /** SPEC §4.2 / §5.3: 横断検索（購読中の全フィード横断）。 */
        const val SCOPE_GLOBAL: String = "global"
    }
}
